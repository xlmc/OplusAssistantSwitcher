package com.ouhuan.oplusassistant.app;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ouhuan.oplusassistant.data.AppExecutors;
import com.ouhuan.oplusassistant.data.RuntimeDebugStore;
import com.ouhuan.oplusassistant.data.RuntimeStatusStore;
import com.ouhuan.oplusassistant.shared.Constants;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 模块 App：注册 XposedService 监听，获取与 LSPosed 管理器通信的 Binder，
 * 用于写入 Remote Preferences（开发书 4.1 RuntimeConfig 的 App 侧配合）。
 */
public class AssistApp extends Application implements XposedServiceHelper.OnServiceListener {

    private static final String TAG = "OplusAssistant";
    private static final long XPOSED_SERVICE_WAIT_TIMEOUT_MS = 15_000L;
    private static final Handler SERVICE_HANDLER = new Handler(Looper.getMainLooper());
    private static final Object SERVICE_STATE_LOCK = new Object();
    private static volatile XposedService service;
    private static volatile Runnable serviceWaitTimeout;
    private static volatile long serviceWaitStartedAt;

    public static XposedService service() {
        return service;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        RuntimeDebugStore.append(context, "app", Constants.EV_APP_ON_CREATE, "application",
            "pid=" + android.os.Process.myPid());
        RuntimeDebugStore.append(context, "app", Constants.EV_XPOSED_LISTENER_REGISTER_BEGIN,
            "xposed_service", "registerListener");
        try {
            XposedServiceHelper.registerListener(this);
            RuntimeDebugStore.append(context, "app",
                Constants.EV_XPOSED_LISTENER_REGISTER_OK, "xposed_service",
                "registerListener returned");
            beginServiceWait(context, "registerListener returned");
        } catch (Throwable ignored) {
            cancelServiceWait();
            RuntimeDebugStore.append(context, "app",
                Constants.EV_XPOSED_LISTENER_REGISTER_FAILED, "xposed_service",
                "registerListener failed", ignored);
            Log.w(TAG, "XposedService listener registration failed: "
                + ignored.getClass().getName() + ": " + ignored.getMessage(), ignored);
        }
    }

    @Override
    public void onServiceBind(XposedService bound) {
        Context context = getApplicationContext();
        if (bound == null) {
            RuntimeDebugStore.append(context, "app",
                Constants.EV_XPOSED_SERVICE_BIND_FAILED, "onServiceBind",
                "framework delivered a null XposedService");
            Log.w(TAG, "XposedService bind callback delivered null service");
            synchronized (SERVICE_STATE_LOCK) {
                if (service == null) {
                    beginServiceWaitLocked(context, "null service delivered");
                }
            }
            return;
        }
        synchronized (SERVICE_STATE_LOCK) {
            service = bound;
            cancelServiceWaitLocked();
            RuntimeDebugStore.append(context, "app", Constants.EV_XPOSED_SERVICE_BIND,
                "xposed_service", "service="
                    + (bound == null ? "null" : bound.getClass().getName()));
        }
        AppExecutors.io().execute(() -> {
            ConfigStore.reconcile(context);
            refreshRuntime(context);
        });
    }

    @Override
    public void onServiceDied(XposedService died) {
        Context context = getApplicationContext();
        synchronized (SERVICE_STATE_LOCK) {
            boolean wasCurrent = died == null || died == service;
            if (!wasCurrent) {
                return;
            }
            service = null;
            cancelServiceWaitLocked();
            RuntimeDebugStore.append(context, "app", Constants.EV_XPOSED_SERVICE_DIED,
                "xposed_service", "service_died");
            beginServiceWaitLocked(context, "service died; waiting for rebind");
        }
        AppExecutors.io().execute(() -> RuntimeStatusStore.updateFramework(context, null));
    }

    /** 官方 helper 没有超时；把“注册成功但没有收到 Binder”变成可确认的诊断状态。 */
    private static void beginServiceWait(Context context, String reason) {
        synchronized (SERVICE_STATE_LOCK) {
            beginServiceWaitLocked(context, reason);
        }
    }

    private static void beginServiceWaitLocked(Context context, String reason) {
        if (service != null || context == null) {
            return;
        }
        cancelServiceWaitLocked();
        Context appContext = context.getApplicationContext();
        serviceWaitStartedAt = System.currentTimeMillis();
        long startedAt = serviceWaitStartedAt;
        RuntimeDebugStore.append(appContext, "app",
            Constants.EV_XPOSED_SERVICE_WAIT_BEGIN, "onServiceBind",
            "reason=" + reason + ",timeoutMs=" + XPOSED_SERVICE_WAIT_TIMEOUT_MS);
        Runnable timeout = () -> onServiceWaitTimeout(appContext, startedAt);
        serviceWaitTimeout = timeout;
        SERVICE_HANDLER.postDelayed(timeout, XPOSED_SERVICE_WAIT_TIMEOUT_MS);
    }

    private static void onServiceWaitTimeout(Context context, long startedAt) {
        synchronized (SERVICE_STATE_LOCK) {
            if (service != null || serviceWaitStartedAt != startedAt
                || serviceWaitTimeout == null) {
                return;
            }
            serviceWaitTimeout = null;
            long waited = Math.max(0L, System.currentTimeMillis() - startedAt);
            String summary = "registerListener returned but onServiceBind was not received"
                + "; waitedMs=" + waited + "; timeoutMs=" + XPOSED_SERVICE_WAIT_TIMEOUT_MS;
            RuntimeDebugStore.append(context, "app",
                Constants.EV_XPOSED_SERVICE_BIND_TIMEOUT, "onServiceBind", summary);
            Log.w(TAG, "XposedService bind timeout: " + summary);
        }
    }

    private static void cancelServiceWait() {
        synchronized (SERVICE_STATE_LOCK) {
            cancelServiceWaitLocked();
        }
    }

    private static void cancelServiceWaitLocked() {
        Runnable timeout = serviceWaitTimeout;
        if (timeout != null) {
            SERVICE_HANDLER.removeCallbacks(timeout);
            serviceWaitTimeout = null;
        }
        serviceWaitStartedAt = 0L;
    }

    /** 刷新官方框架握手、system_server 目标及自定义 Binder ping 快照。 */
    public static void refreshRuntime(Context context) {
        if (context == null) {
            return;
        }
        XposedService bound = service;
        RuntimeStatusStore.updateFramework(context, bound);
        RuntimeDebugStore.append(context, "app", Constants.EV_RUNTIME_BINDER_PING_BEGIN,
            "runtime_ping", "callback=" + (bound == null ? "service_unbound" : "checking"));
        Bundle pong = RuntimeStatusService.pingSystemServer();
        if (pong != null) {
            RuntimeStatusStore.markPing(context, "OK", "pong received");
            RuntimeDebugStore.append(context, "app", Constants.EV_RUNTIME_BINDER_PING_OK,
                "runtime_ping", "pong received");
            RuntimeStatusService.applyPong(context, pong);
        } else {
            String pingFailure = RuntimeStatusService.lastPingFailure();
            String summary = pingFailure.isEmpty() ? (bound == null
                ? "callback unavailable; XposedService not bound"
                : "no pong; callback unavailable or transaction failed") : pingFailure;
            RuntimeStatusStore.markPing(context, "FAILED", summary);
            RuntimeDebugStore.append(context, "app", Constants.EV_RUNTIME_BINDER_PING_FAILED,
                "runtime_ping", summary);
            Log.w(TAG, "Runtime Binder ping failed: " + summary);
        }
    }
}
