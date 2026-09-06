package com.ouhuan.oplusassistant.app;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
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
    private static volatile XposedService service;

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
        } catch (Throwable ignored) {
            RuntimeDebugStore.append(context, "app",
                Constants.EV_XPOSED_LISTENER_REGISTER_FAILED, "xposed_service",
                "registerListener failed", ignored);
            Log.w(TAG, "XposedService listener registration failed: "
                + ignored.getClass().getName() + ": " + ignored.getMessage(), ignored);
        }
    }

    @Override
    public void onServiceBind(XposedService bound) {
        service = bound;
        Context context = getApplicationContext();
        RuntimeDebugStore.append(context, "app", Constants.EV_XPOSED_SERVICE_BIND,
            "xposed_service", "service=" + (bound == null ? "null" : bound.getClass().getName()));
        AppExecutors.io().execute(() -> {
            ConfigStore.reconcile(context);
            refreshRuntime(context);
        });
    }

    @Override
    public void onServiceDied(XposedService died) {
        boolean wasCurrent = died == service;
        if (died == service) {
            service = null;
        }
        if (wasCurrent) {
            Context context = getApplicationContext();
            RuntimeDebugStore.append(context, "app", Constants.EV_XPOSED_SERVICE_DIED,
                "xposed_service", "service_died");
            AppExecutors.io().execute(() -> RuntimeStatusStore.updateFramework(context, null));
        }
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
