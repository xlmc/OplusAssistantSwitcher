package com.ouhuan.oplusassistant.xposed;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;

import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.LogEvent;
import com.ouhuan.oplusassistant.shared.RuntimeStatusContract;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;

/**
 * system_server 侧运行态桥：通过显式绑定的 App Service 推送日志/状态，并提供窄 ping
 * 回调给诊断页。系统上下文和 App Service 尚未就绪时只在内存中保留有限快照。
 */
public final class RuntimeStatusBridge {

    private static final int PENDING_LIMIT = 64;

    public interface LifecycleSink {
        void onEvent(String event, String summary);
    }

    private final XposedInterface xposed;
    private final LifecycleSink lifecycleSink;
    private final Object lock = new Object();
    private final Bundle state = new Bundle();
    private final ArrayDeque<Bundle> pendingEvents = new ArrayDeque<>();
    private final CallbackBinder callback = new CallbackBinder();
    private volatile int moduleUid;
    private final long moduleLoadedAt;

    private volatile ContextProvider contextProvider;
    private volatile IBinder appService;
    private volatile boolean bindAttempted;
    private volatile boolean fallback;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            handleServiceConnected(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (lock) {
                appService = null;
                fallback = true;
                state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
                    RuntimeStatusContract.CHANNEL_DISCONNECTED);
            }
            String summary = "runtime service disconnected: " + name;
            log(summary);
            emitLifecycle(Constants.EV_RUNTIME_BINDER_BIND_FAILED, summary);
        }
    };

    public RuntimeStatusBridge(XposedInterface xposed) {
        this(xposed, null);
    }

    public RuntimeStatusBridge(XposedInterface xposed, LifecycleSink lifecycleSink) {
        this.xposed = xposed;
        this.lifecycleSink = lifecycleSink;
        this.moduleLoadedAt = System.currentTimeMillis();
        int uid = -1;
        try {
            if (xposed != null && xposed.getModuleApplicationInfo() != null) {
                uid = xposed.getModuleApplicationInfo().uid;
            }
        } catch (Throwable ignored) {
            log("read module uid failed", ignored);
        }
        this.moduleUid = uid;

        state.putInt(RuntimeStatusContract.KEY_PROTOCOL_VERSION,
            RuntimeStatusContract.PROTOCOL_VERSION);
        state.putString(RuntimeStatusContract.KEY_PROCESS_NAME, "system_server");
        state.putLong(RuntimeStatusContract.KEY_MODULE_UID, uid);
        state.putLong(RuntimeStatusContract.KEY_PEER_UID, Process.SYSTEM_UID);
        state.putString(RuntimeStatusContract.KEY_PEER_PROCESS, "system_server");
        state.putString(RuntimeStatusContract.KEY_MODULE_VERSION_NAME,
            ModuleBuildInfo.VERSION_NAME);
        state.putLong(RuntimeStatusContract.KEY_MODULE_VERSION_CODE,
            ModuleBuildInfo.VERSION_CODE);
        state.putLong(RuntimeStatusContract.KEY_MODULE_LOADED_AT, moduleLoadedAt);
        state.putString(RuntimeStatusContract.KEY_HOOK_STRATEGY,
            Constants.HOOK_STRATEGY_COLOROS16);
        state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "MODULE_NOT_LOADED");
        state.putString(RuntimeStatusContract.KEY_HOOK_STATUS, "-");
        state.putString(RuntimeStatusContract.KEY_CONTEXT_STATE, "UNKNOWN");
        state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
            RuntimeStatusContract.CHANNEL_WAITING);
        state.putString(RuntimeStatusContract.KEY_POWER_ASSIST_STATUS,
            Constants.EV_POWER_ASSIST_NOT_MATCHED);
        state.putLong(RuntimeStatusContract.KEY_POWER_ASSIST_MATCHED_AT, 0L);
        readFrameworkMetadata();
    }

    /** system_server context 捕获后调用；未捕获时会保留 WAITING 状态。 */
    public void start(ContextProvider provider) {
        contextProvider = provider;
        Context context = provider == null ? null : provider.get();
        if (context == null) {
            return;
        }
        resolveModuleUid(context);
        synchronized (lock) {
            if (appService != null || bindAttempted) {
                return;
            }
            bindAttempted = true;
        }
        emitLifecycle(Constants.EV_RUNTIME_BINDER_BIND_BEGIN,
            "service=" + RuntimeStatusContract.SERVICE_CLASS + ",moduleUid=" + moduleUid);
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(Constants.MODULE_PACKAGE,
                RuntimeStatusContract.SERVICE_CLASS));
            boolean bound = context.bindServiceAsUser(intent, connection,
                Context.BIND_AUTO_CREATE | Context.BIND_NOT_FOREGROUND,
                // system_server 的公开 user handle，避免依赖隐藏的 SYSTEM 常量。
                Process.myUserHandle());
            if (!bound) {
                markChannelFailed("bindServiceAsUser returned false");
            }
        } catch (Throwable t) {
            markChannelFailed("bindServiceAsUser", t);
        }
    }

    public long moduleLoadedAt() {
        return moduleLoadedAt;
    }

    public boolean isFallback() {
        return fallback;
    }

    /** systemReady 后重试一次早期可能因 PackageManager 尚未就绪而失败的绑定。 */
    public void retry(ContextProvider provider) {
        synchronized (lock) {
            if (!fallback || appService != null) {
                return;
            }
            fallback = false;
            bindAttempted = false;
            state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
                RuntimeStatusContract.CHANNEL_WAITING);
        }
        start(provider);
    }

    /** Xposed ApplicationInfo 不可用时，仅补查 UID；版本仍只来自编译进 dex 的常量。 */
    private void resolveModuleUid(Context context) {
        if (moduleUid > 0 || context == null) {
            return;
        }
        try {
            int resolved = context.getPackageManager()
                .getApplicationInfo(Constants.MODULE_PACKAGE, 0).uid;
            if (resolved > 0) {
                synchronized (lock) {
                    if (moduleUid <= 0) {
                        moduleUid = resolved;
                        state.putLong(RuntimeStatusContract.KEY_MODULE_UID, resolved);
                    }
                }
                log("resolved module uid from system PackageManager: " + resolved);
            }
        } catch (Throwable t) {
            log("resolve module uid from system PackageManager failed", t);
        }
    }

    /** 发送 Hook/调用事件；未连接时排队，通道明确失败时让调用方走广播降级。 */
    public boolean publishEvent(LogEvent event) {
        if (event == null) {
            return true;
        }
        Bundle payload;
        synchronized (lock) {
            recordEventLocked(event);
            payload = eventBundle(event, state.getLong(
                RuntimeStatusContract.KEY_EVENT_SEQUENCE, 0L));
        }
        start(contextProvider);
        if (fallback) {
            return false;
        }
        SendResult result = send(RuntimeStatusContract.TRANSACTION_PUSH_EVENT, payload);
        if (result == SendResult.NOT_CONNECTED) {
            synchronized (lock) {
                if (pendingEvents.size() >= PENDING_LIMIT) {
                    pendingEvents.removeFirst();
                }
                pendingEvents.addLast(new Bundle(payload));
            }
            log("queued runtime event=" + event.event);
            return true;
        }
        if (result == SendResult.SENT) {
            sendCurrentState();
            return true;
        }
        return false;
    }

    /** 发送状态快照；candidateEntries 为空时不清理已有候选列表。 */
    public boolean publishState(Map<String, String> fields,
                                List<String> candidateEntries) {
        Bundle snapshot;
        synchronized (lock) {
            if (fields != null) {
                for (Map.Entry<String, String> entry : fields.entrySet()) {
                    state.putString(entry.getKey(), entry.getValue() == null
                        ? "" : entry.getValue());
                }
            }
            if (candidateEntries != null) {
                state.putStringArrayList(RuntimeStatusContract.KEY_CANDIDATES,
                    new ArrayList<>(candidateEntries));
            }
            snapshot = new Bundle(state);
        }
        start(contextProvider);
        if (fallback) {
            return false;
        }
        SendResult result = send(RuntimeStatusContract.TRANSACTION_PUSH_STATE, snapshot);
        if (result == SendResult.NOT_CONNECTED) {
            return true;
        }
        return result == SendResult.SENT;
    }

    /** system_server 成功读取 Remote Preferences 后推送诊断配置快照。 */
    public void updateConfig(RuntimeConfig.Snapshot config) {
        if (config == null) {
            return;
        }
        synchronized (lock) {
            state.putBoolean(RuntimeStatusContract.KEY_CONFIG_KNOWN, true);
            state.putBoolean(RuntimeStatusContract.KEY_CONFIG_ENABLED, config.enabled);
            state.putBoolean(RuntimeStatusContract.KEY_CONFIG_DETAIL,
                config.detailDiagnostics);
            state.putString(RuntimeStatusContract.KEY_CONFIG_SELECTED_PACKAGE,
                safe(config.selectedPackage));
            state.putString(RuntimeStatusContract.KEY_CONFIG_SELECTED_COMPONENT,
                safe(config.selectedComponent));
            state.putLong(RuntimeStatusContract.KEY_CONFIG_UPDATED_AT,
                System.currentTimeMillis());
        }
        publishState(null, null);
    }

    public Bundle snapshot() {
        synchronized (lock) {
            return new Bundle(state);
        }
    }

    private void handleServiceConnected(IBinder service) {
        if (service == null || !registerCallback(service)) {
            markChannelFailed("register callback failed");
            return;
        }
        synchronized (lock) {
            appService = service;
            fallback = false;
            state.putLong(RuntimeStatusContract.KEY_PEER_UID, moduleUid);
            state.putString(RuntimeStatusContract.KEY_PEER_PROCESS, "module_app");
            state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
                RuntimeStatusContract.CHANNEL_READY);
        }
        emitLifecycle(Constants.EV_RUNTIME_BINDER_BIND_OK,
            "service_connected,moduleUid=" + moduleUid + ",peer=module_app");
        LogEvent ready = syntheticHookEvent(Constants.EV_STATE_CHANNEL_READY,
            "Binder service connected");
        publishEvent(ready);
        flushPending();
        log("runtime service ready");
    }

    private boolean registerCallback(IBinder service) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(RuntimeStatusContract.SERVICE_DESCRIPTOR);
            data.writeStrongBinder(callback);
            if (!service.transact(RuntimeStatusContract.TRANSACTION_REGISTER_CALLBACK,
                data, reply, 0)) {
                log("register callback transact returned false");
                return false;
            }
            reply.readException();
            return true;
        } catch (Throwable t) {
            log("register callback failed", t);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void flushPending() {
        SendResult stateResult = sendCurrentState();
        if (stateResult == SendResult.FAILED) {
            return;
        }
        while (true) {
            Bundle event;
            synchronized (lock) {
                event = pendingEvents.pollFirst();
            }
            if (event == null) {
                break;
            }
            if (send(RuntimeStatusContract.TRANSACTION_PUSH_EVENT, event)
                    != SendResult.SENT) {
                return;
            }
        }
        sendCurrentState();
    }

    private SendResult sendCurrentState() {
        Bundle snapshot = snapshot();
        return send(RuntimeStatusContract.TRANSACTION_PUSH_STATE, snapshot);
    }

    private SendResult send(int transaction, Bundle payload) {
        IBinder service = appService;
        if (service == null) {
            return SendResult.NOT_CONNECTED;
        }
        if (!service.isBinderAlive()) {
            markChannelFailed("app service binder is not alive");
            return SendResult.FAILED;
        }
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(RuntimeStatusContract.SERVICE_DESCRIPTOR);
            data.writeBundle(payload);
            if (!service.transact(transaction, data, null, IBinder.FLAG_ONEWAY)) {
                markChannelFailed("transaction rejected: " + transaction);
                return SendResult.FAILED;
            }
            return SendResult.SENT;
        } catch (Throwable t) {
            markChannelFailed("transaction " + transaction, t);
            return SendResult.FAILED;
        } finally {
            data.recycle();
        }
    }

    private void markChannelFailed(String summary) {
        markChannelFailed(summary, null);
    }

    private void markChannelFailed(String summary, Throwable error) {
        boolean shouldEmit;
        synchronized (lock) {
            shouldEmit = !fallback
                || !RuntimeStatusContract.CHANNEL_FAILED.equals(
                    state.getString(RuntimeStatusContract.KEY_CHANNEL_STATE, ""));
            appService = null;
            fallback = true;
            state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
                RuntimeStatusContract.CHANNEL_FAILED);
        }
        String detail = appendThrowable(summary, error);
        log("runtime channel failed: " + detail);
        if (shouldEmit) {
            emitLifecycle(Constants.EV_RUNTIME_BINDER_BIND_FAILED, detail);
        }
    }

    private void recordEventLocked(LogEvent event) {
        String eventName = safe(event.event);
        long sequence = state.getLong(RuntimeStatusContract.KEY_EVENT_SEQUENCE, 0L) + 1L;
        state.putLong(RuntimeStatusContract.KEY_EVENT_SEQUENCE, sequence);
        state.putString(RuntimeStatusContract.KEY_LAST_EVENT, eventName);
        state.putLong(RuntimeStatusContract.KEY_LAST_EVENT_AT, parseLong(event.timestamp));
        if (Constants.KIND_HOOK.equals(event.kind) && isLifecycleEvent(eventName)) {
            state.putString(RuntimeStatusContract.KEY_HOOK_STATUS, safe(event.hookStatus));
        }
        state.putString(RuntimeStatusContract.KEY_HOOK_STRATEGY,
            safe(event.hookStrategy).isEmpty()
                ? Constants.HOOK_STRATEGY_COLOROS16 : event.hookStrategy);
        switch (eventName) {
            case Constants.EV_MODULE_LOADED:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "MODULE_LOADED");
                break;
            case Constants.EV_SYSTEM_SERVER_STARTING:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "SYSTEM_SERVER_STARTING");
                break;
            case Constants.EV_AMS_SYSTEM_READY:
            case Constants.EV_SYSTEM_SERVER_READY:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "AMS_SYSTEM_READY");
                break;
            case Constants.EV_SYSTEM_CONTEXT_READY:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "SYSTEM_CONTEXT_READY");
                state.putString(RuntimeStatusContract.KEY_CONTEXT_STATE, "READY");
                break;
            case Constants.EV_SYSTEM_CONTEXT_UNAVAILABLE:
                state.putString(RuntimeStatusContract.KEY_CONTEXT_STATE, "UNAVAILABLE");
                break;
            case Constants.EV_HOOK_CLASS_FOUND:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "HOOK_CLASS_FOUND");
                break;
            case Constants.EV_HOOK_METHOD_FOUND:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "HOOK_METHOD_FOUND");
                break;
            case Constants.EV_HOOK_CLASS_NOT_FOUND:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "HOOK_CLASS_NOT_FOUND");
                break;
            case Constants.EV_HOOK_METHOD_NOT_FOUND:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "HOOK_METHOD_NOT_FOUND");
                break;
            case Constants.EV_HOOK_TARGET_NOT_FOUND:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "HOOK_TARGET_NOT_FOUND");
                break;
            case Constants.EV_HOOK_INSTALLED:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "HOOK_INSTALLED");
                break;
            case Constants.EV_HOOK_FAILED:
            case Constants.EV_HOOK_INSTALL_FAILED:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "HOOK_INSTALL_FAILED");
                break;
            case Constants.EV_ROM_UNSUPPORTED:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE, "ROM_UNSUPPORTED");
                break;
            case Constants.EV_STATE_CHANNEL_READY:
                state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
                    RuntimeStatusContract.CHANNEL_READY);
                break;
            case Constants.EV_STATE_CHANNEL_FAILED:
                state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
                    RuntimeStatusContract.CHANNEL_FAILED);
                break;
            case Constants.EV_RUNTIME_BINDER_BIND_BEGIN:
                state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
                    RuntimeStatusContract.CHANNEL_WAITING);
                break;
            case Constants.EV_RUNTIME_BINDER_BIND_OK:
                state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
                    RuntimeStatusContract.CHANNEL_READY);
                break;
            case Constants.EV_RUNTIME_BINDER_BIND_FAILED:
                state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
                    RuntimeStatusContract.CHANNEL_FAILED);
                break;
            case Constants.EV_CONFIG_READ_FAILED:
                state.putBoolean(RuntimeStatusContract.KEY_CONFIG_KNOWN, false);
                break;
            case Constants.EV_RESOLVER_QUERY_FAILED:
                state.putString(RuntimeStatusContract.KEY_HOOK_STAGE,
                    "RESOLVER_QUERY_FAILED");
                break;
            case Constants.EV_POWER_ASSIST_EVENT_MATCHED:
            case Constants.EV_POWER_ASSIST_0X3F3_MATCHED:
                state.putLong(RuntimeStatusContract.KEY_POWER_ASSIST_MATCHED_AT,
                    parseLong(event.timestamp));
                state.putString(RuntimeStatusContract.KEY_POWER_ASSIST_STATUS,
                    "MATCHED");
                break;
            case Constants.EV_POWER_ASSIST_NOT_MATCHED:
                state.putString(RuntimeStatusContract.KEY_POWER_ASSIST_STATUS,
                    Constants.EV_POWER_ASSIST_NOT_MATCHED);
                break;
            default:
                break;
        }
    }

    private Bundle eventBundle(LogEvent event, long sequence) {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : event.toMap().entrySet()) {
            bundle.putString(entry.getKey(), entry.getValue());
        }
        bundle.putLong(RuntimeStatusContract.KEY_EVENT_SEQUENCE, sequence);
        return bundle;
    }

    private LogEvent syntheticHookEvent(String event, String summary) {
        LogEvent result = new LogEvent();
        result.kind = Constants.KIND_HOOK;
        result.event = event;
        result.timestamp = String.valueOf(System.currentTimeMillis());
        result.hookStrategy = Constants.HOOK_STRATEGY_COLOROS16;
        result.hookStatus = event;
        result.exceptionSummary = summary;
        return result;
    }

    private void readFrameworkMetadata() {
        if (xposed == null) {
            return;
        }
        try {
            state.putString(RuntimeStatusContract.KEY_FRAMEWORK_NAME,
                safe(xposed.getFrameworkName()));
        } catch (Throwable ignored) {
            log("read framework name failed", ignored);
        }
        try {
            state.putString(RuntimeStatusContract.KEY_FRAMEWORK_VERSION,
                safe(xposed.getFrameworkVersion()));
        } catch (Throwable ignored) {
            log("read framework version failed", ignored);
        }
        try {
            state.putLong(RuntimeStatusContract.KEY_FRAMEWORK_VERSION_CODE,
                xposed.getFrameworkVersionCode());
        } catch (Throwable ignored) {
            log("read framework version code failed", ignored);
        }
        try {
            state.putLong(RuntimeStatusContract.KEY_FRAMEWORK_API, xposed.getApiVersion());
        } catch (Throwable ignored) {
            log("read framework api failed", ignored);
        }
        try {
            state.putLong(RuntimeStatusContract.KEY_FRAMEWORK_PROPERTIES,
                xposed.getFrameworkProperties());
        } catch (Throwable ignored) {
            log("read framework properties failed", ignored);
        }
    }

    private final class CallbackBinder extends Binder {
        CallbackBinder() {
            attachInterface(null, RuntimeStatusContract.CALLBACK_DESCRIPTOR);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) {
                    reply.writeString(RuntimeStatusContract.CALLBACK_DESCRIPTOR);
                }
                return true;
            }
            data.enforceInterface(RuntimeStatusContract.CALLBACK_DESCRIPTOR);
            int callingUid = Binder.getCallingUid();
            if (moduleUid <= 0 || callingUid != moduleUid) {
                log("runtime ping rejected: senderUid=" + callingUid
                    + ",expectedModuleUid=" + moduleUid);
                throw new SecurityException("runtime ping accepts module uid only");
            }
            if (code == RuntimeStatusContract.TRANSACTION_PING) {
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeBundle(snapshot());
                }
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    private enum SendResult {
        SENT,
        NOT_CONNECTED,
        FAILED
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isLifecycleEvent(String event) {
        return Constants.EV_MODULE_LOADED.equals(event)
            || Constants.EV_SYSTEM_SERVER_STARTING.equals(event)
            || Constants.EV_SYSTEM_CONTEXT_READY.equals(event)
            || Constants.EV_SYSTEM_CONTEXT_UNAVAILABLE.equals(event)
            || Constants.EV_AMS_SYSTEM_READY.equals(event)
            || Constants.EV_SYSTEM_SERVER_READY.equals(event)
            || Constants.EV_HOOK_CLASS_FOUND.equals(event)
            || Constants.EV_HOOK_METHOD_FOUND.equals(event)
            || Constants.EV_HOOK_CLASS_NOT_FOUND.equals(event)
            || Constants.EV_HOOK_METHOD_NOT_FOUND.equals(event)
            || Constants.EV_HOOK_INSTALLED.equals(event)
            || Constants.EV_HOOK_FAILED.equals(event)
            || Constants.EV_HOOK_INSTALL_FAILED.equals(event)
            || Constants.EV_HOOK_TARGET_NOT_FOUND.equals(event)
            || Constants.EV_ROM_UNSUPPORTED.equals(event)
            || Constants.EV_RUNTIME_BINDER_BIND_BEGIN.equals(event)
            || Constants.EV_RUNTIME_BINDER_BIND_OK.equals(event)
            || Constants.EV_RUNTIME_BINDER_BIND_FAILED.equals(event)
            || Constants.EV_CONFIG_READ.equals(event)
            || Constants.EV_CONFIG_READ_FAILED.equals(event)
            || Constants.EV_RESOLVER_QUERY_FAILED.equals(event);
    }

    private void emitLifecycle(String event, String summary) {
        LifecycleSink sink = lifecycleSink;
        if (sink == null) {
            return;
        }
        try {
            sink.onEvent(event, summary);
        } catch (Throwable t) {
            log("lifecycle sink failed for " + event, t);
        }
    }

    private void log(String message) {
        log(message, null);
    }

    private void log(String message, Throwable error) {
        try {
            if (xposed != null) {
                xposed.log(error == null ? android.util.Log.INFO : android.util.Log.WARN,
                    Constants.MODULE_PACKAGE,
                    error == null ? message : message + ": " + appendThrowable("", error));
                return;
            }
        } catch (Throwable loggingFailure) {
            android.util.Log.w(Constants.MODULE_PACKAGE,
                message + " (Xposed log failed: " + loggingFailure.getClass().getName()
                    + ": " + loggingFailure.getMessage() + ")", error);
        }
        android.util.Log.w(Constants.MODULE_PACKAGE,
            error == null ? message : message + ": " + appendThrowable("", error), error);
    }

    private static String appendThrowable(String summary, Throwable error) {
        if (error == null) {
            return summary;
        }
        String suffix = error.getClass().getName() + ": " + error.getMessage();
        return summary == null || summary.isEmpty() ? suffix : summary + "; " + suffix;
    }
}
