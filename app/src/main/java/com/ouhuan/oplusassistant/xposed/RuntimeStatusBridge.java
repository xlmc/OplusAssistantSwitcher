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
import android.os.UserHandle;

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

    private final XposedInterface xposed;
    private final Object lock = new Object();
    private final Bundle state = new Bundle();
    private final ArrayDeque<Bundle> pendingEvents = new ArrayDeque<>();
    private final CallbackBinder callback = new CallbackBinder();
    private final int moduleUid;
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
            log("runtime service disconnected: " + name);
        }
    };

    public RuntimeStatusBridge(XposedInterface xposed) {
        this.xposed = xposed;
        this.moduleLoadedAt = System.currentTimeMillis();
        int uid = -1;
        try {
            if (xposed != null && xposed.getModuleApplicationInfo() != null) {
                uid = xposed.getModuleApplicationInfo().uid;
            }
        } catch (Throwable ignored) {
        }
        this.moduleUid = uid;

        state.putInt(RuntimeStatusContract.KEY_PROTOCOL_VERSION,
            RuntimeStatusContract.PROTOCOL_VERSION);
        state.putString(RuntimeStatusContract.KEY_PROCESS_NAME, "system_server");
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
        synchronized (lock) {
            if (appService != null || bindAttempted) {
                return;
            }
            bindAttempted = true;
        }
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(Constants.MODULE_PACKAGE,
                RuntimeStatusContract.SERVICE_CLASS));
            boolean bound = context.bindServiceAsUser(intent, connection,
                Context.BIND_AUTO_CREATE | Context.BIND_NOT_FOREGROUND,
                // UserHandle 的公开 SDK 没有 SYSTEM/USER_SYSTEM 常量；0 是 system user。
                new UserHandle(0));
            if (!bound) {
                markChannelFailed("bindServiceAsUser returned false");
            }
        } catch (Throwable t) {
            markChannelFailed("bindServiceAsUser: " + t.getClass().getSimpleName()
                + ": " + t.getMessage());
        }
    }

    public long moduleLoadedAt() {
        return moduleLoadedAt;
    }

    public boolean isFallback() {
        return fallback;
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
            state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
                RuntimeStatusContract.CHANNEL_READY);
        }
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
                return false;
            }
            reply.readException();
            return true;
        } catch (Throwable t) {
            log("register callback failed: " + t);
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
        if (service == null || !service.isBinderAlive()) {
            return SendResult.NOT_CONNECTED;
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
            markChannelFailed("transaction " + transaction + ": "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
            return SendResult.FAILED;
        } finally {
            data.recycle();
        }
    }

    private void markChannelFailed(String summary) {
        synchronized (lock) {
            appService = null;
            fallback = true;
            state.putString(RuntimeStatusContract.KEY_CHANNEL_STATE,
                RuntimeStatusContract.CHANNEL_FAILED);
        }
        log("runtime channel failed: " + summary);
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
            case Constants.EV_POWER_ASSIST_EVENT_MATCHED:
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
        }
        try {
            state.putString(RuntimeStatusContract.KEY_FRAMEWORK_VERSION,
                safe(xposed.getFrameworkVersion()));
        } catch (Throwable ignored) {
        }
        try {
            state.putLong(RuntimeStatusContract.KEY_FRAMEWORK_VERSION_CODE,
                xposed.getFrameworkVersionCode());
        } catch (Throwable ignored) {
        }
        try {
            state.putLong(RuntimeStatusContract.KEY_FRAMEWORK_API, xposed.getApiVersion());
        } catch (Throwable ignored) {
        }
        try {
            state.putLong(RuntimeStatusContract.KEY_FRAMEWORK_PROPERTIES,
                xposed.getFrameworkProperties());
        } catch (Throwable ignored) {
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
            if (Binder.getCallingUid() != moduleUid) {
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
            || Constants.EV_HOOK_CLASS_FOUND.equals(event)
            || Constants.EV_HOOK_METHOD_FOUND.equals(event)
            || Constants.EV_HOOK_CLASS_NOT_FOUND.equals(event)
            || Constants.EV_HOOK_METHOD_NOT_FOUND.equals(event)
            || Constants.EV_HOOK_INSTALLED.equals(event)
            || Constants.EV_HOOK_FAILED.equals(event)
            || Constants.EV_HOOK_INSTALL_FAILED.equals(event)
            || Constants.EV_ROM_UNSUPPORTED.equals(event);
    }

    private void log(String message) {
        try {
            if (xposed != null) {
                xposed.log(android.util.Log.INFO, Constants.MODULE_PACKAGE, message);
            }
        } catch (Throwable ignored) {
        }
    }
}
