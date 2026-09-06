package com.ouhuan.oplusassistant.app;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.ouhuan.oplusassistant.data.AppExecutors;
import com.ouhuan.oplusassistant.data.AssistantStateStore;
import com.ouhuan.oplusassistant.data.HookStateStore;
import com.ouhuan.oplusassistant.data.LogDb;
import com.ouhuan.oplusassistant.data.LogEntity;
import com.ouhuan.oplusassistant.data.RuntimeDebugStore;
import com.ouhuan.oplusassistant.data.RuntimeStatusStore;
import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.RuntimeStatusContract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * system_server 与模块 App 之间的窄 Binder 桥。
 *
 * <p>此服务只接受 system uid 的注册、状态和日志事务；具体日志/状态落库始终放到
 * App 侧单线程执行器，避免 Binder 线程直接触碰 Room。</p>
 */
public final class RuntimeStatusService extends Service {

    private static final String TAG = "OplusAssistant";
    private static final Object CALLBACK_LOCK = new Object();
    private static volatile IBinder systemServerCallback;
    private static volatile IBinder.DeathRecipient callbackDeathRecipient;
    private static volatile android.content.Context serviceContext;
    private static volatile String lastPingFailure = "";

    private final IBinder binder = new StatusBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        serviceContext = getApplicationContext();
        Log.i(TAG, "RuntimeStatusService created");
    }

    @Override
    public void onDestroy() {
        clearCallback(null, "service destroyed");
        serviceContext = null;
        Log.i(TAG, "RuntimeStatusService destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /** App 诊断页调用：请求 system_server 返回运行态快照。 */
    public static Bundle pingSystemServer() {
        IBinder callback = systemServerCallback;
        if (callback == null || !callback.isBinderAlive()) {
            lastPingFailure = "callback unavailable";
            Log.w(TAG, "Runtime Binder ping skipped: callback is unavailable");
            return null;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(RuntimeStatusContract.CALLBACK_DESCRIPTOR);
            boolean delivered = callback.transact(RuntimeStatusContract.TRANSACTION_PING,
                data, reply, 0);
            if (!delivered) {
                lastPingFailure = "transact returned false";
                Log.w(TAG, "Runtime Binder ping transact returned false");
                return null;
            }
            reply.readException();
            Bundle result = reply.readBundle(RuntimeStatusService.class.getClassLoader());
            if (result != null) {
                result.setClassLoader(RuntimeStatusService.class.getClassLoader());
            } else {
                lastPingFailure = "empty pong bundle";
                return null;
            }
            lastPingFailure = "";
            return result;
        } catch (Throwable ignored) {
            lastPingFailure = ignored.getClass().getName() + ": " + ignored.getMessage();
            Log.w(TAG, "Runtime Binder ping failed: " + ignored.getClass().getName() + ": "
                + ignored.getMessage(), ignored);
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public static String lastPingFailure() {
        return lastPingFailure == null ? "" : lastPingFailure;
    }

    /** 将一次 ping 结果按与推送相同的路径持久化。 */
    public static void applyPong(android.content.Context context, Bundle state) {
        if (context == null || state == null) {
            return;
        }
        // 调用方 AssistApp.refreshRuntime 已在 AppExecutors.io() 中运行；同步落快照，
        // 让同一轮诊断刷新立即看到 pong，而不会读到上一轮状态。
        persistState(context, state);
    }

    private static void persistState(android.content.Context context, Bundle state) {
        try {
            RuntimeStatusStore.apply(context, state);
            ArrayList<String> candidates = state.getStringArrayList(
                RuntimeStatusContract.KEY_CANDIDATES);
            if (hasAssistantState(state) || candidates != null) {
                AssistantStateStore.apply(context, toMap(state), candidates);
            }
        } catch (Throwable ignored) {
            RuntimeDebugStore.append(context, "app", Constants.EV_STATE_CHANNEL_FAILED,
                "state_persist", "state persistence failed", ignored);
            Log.w(TAG, "Runtime state persistence failed: " + ignored.getClass().getName()
                + ": " + ignored.getMessage(), ignored);
        }
    }

    private static void persistEvent(android.content.Context context, Bundle event) {
        try {
            Map<String, String> data = toMap(event);
            RuntimeStatusStore.applyEvent(context, event);
            LogEntity entity = LogEntity.fromMap(data);
            LogDb.get(context).dao().insert(entity);
            if (Constants.KIND_HOOK.equals(entity.kind)) {
                HookStateStore.update(context, entity);
            }
        } catch (Throwable ignored) {
            String eventName = event == null ? "" : event.getString(Constants.FIELD_EVENT, "");
            RuntimeDebugStore.append(context, "app", Constants.EV_STATE_CHANNEL_FAILED,
                "event_persist", "event=" + eventName + " persistence failed", ignored);
            Log.w(TAG, "Runtime event persistence failed: " + ignored.getClass().getName()
                + ": " + ignored.getMessage(), ignored);
        }
    }

    private static boolean hasAssistantState(Bundle state) {
        return state.containsKey(RuntimeStatusContract.KEY_CURRENT_NAME)
            || state.containsKey(RuntimeStatusContract.KEY_CURRENT_PACKAGE)
            || state.containsKey(RuntimeStatusContract.KEY_CURRENT_COMPONENT)
            || state.containsKey(RuntimeStatusContract.KEY_CURRENT_SOURCE)
            || state.containsKey(RuntimeStatusContract.KEY_STATE_TIMESTAMP);
    }

    private static Map<String, String> toMap(Bundle bundle) {
        Map<String, String> result = new HashMap<>();
        if (bundle == null) {
            return result;
        }
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value == null || value instanceof ArrayList) {
                continue;
            }
            result.put(key, String.valueOf(value));
        }
        return result;
    }

    private final class StatusBinder extends Binder {

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) {
                    reply.writeString(RuntimeStatusContract.SERVICE_DESCRIPTOR);
                }
                return true;
            }
            data.enforceInterface(RuntimeStatusContract.SERVICE_DESCRIPTOR);
            enforceSystemCaller();
            switch (code) {
                case RuntimeStatusContract.TRANSACTION_REGISTER_CALLBACK:
                    IBinder callback = data.readStrongBinder();
                    if (callback == null) {
                        throw new RemoteException("system_server callback is null");
                    }
                    registerSystemServerCallback(callback);
                    RuntimeStatusStore.markPeer(RuntimeStatusService.this, Binder.getCallingUid(),
                        "system_server");
                    RuntimeDebugStore.append(RuntimeStatusService.this, "app",
                        Constants.EV_RUNTIME_BINDER_BIND_OK, "runtime_service",
                        "system_server callback registered uid=" + Binder.getCallingUid());
                    if (reply != null) {
                        reply.writeNoException();
                    }
                    return true;
                case RuntimeStatusContract.TRANSACTION_PUSH_STATE:
                    Bundle state = data.readBundle(RuntimeStatusService.class.getClassLoader());
                    if (state != null) {
                        AppExecutors.io().execute(() -> persistState(
                            RuntimeStatusService.this, state));
                    }
                    return true;
                case RuntimeStatusContract.TRANSACTION_PUSH_EVENT:
                    Bundle event = data.readBundle(RuntimeStatusService.class.getClassLoader());
                    if (event != null) {
                        AppExecutors.io().execute(() -> persistEvent(
                            RuntimeStatusService.this, event));
                    }
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private void registerSystemServerCallback(IBinder callback) throws RemoteException {
            IBinder previous;
            IBinder.DeathRecipient previousRecipient;
            IBinder.DeathRecipient newRecipient = () ->
                clearCallback(callback, "system_server callback binder died");
            synchronized (CALLBACK_LOCK) {
                previous = systemServerCallback;
                previousRecipient = callbackDeathRecipient;
                systemServerCallback = callback;
                callbackDeathRecipient = newRecipient;
                try {
                    callback.linkToDeath(newRecipient, 0);
                } catch (RemoteException e) {
                    systemServerCallback = previous;
                    callbackDeathRecipient = previousRecipient;
                    throw e;
                }
            }
            if (previous != null && previousRecipient != null) {
                try {
                    previous.unlinkToDeath(previousRecipient, 0);
                } catch (Throwable ignored) {
                    Log.w(TAG, "Unable to unlink previous system_server callback", ignored);
                }
            }
        }

        private void enforceSystemCaller() {
            if (Binder.getCallingUid() != Process.SYSTEM_UID) {
                RuntimeDebugStore.append(RuntimeStatusService.this, "app",
                    Constants.EV_RUNTIME_BINDER_BIND_FAILED, "runtime_service",
                    "rejected uid=" + Binder.getCallingUid());
                Log.w(TAG, "Rejected RuntimeStatusService caller uid=" + Binder.getCallingUid());
                throw new SecurityException("RuntimeStatusService accepts system uid only");
            }
        }
    }

    private static void clearCallback(IBinder expected, String reason) {
        IBinder previous = null;
        IBinder.DeathRecipient previousRecipient = null;
        synchronized (CALLBACK_LOCK) {
            if (expected != null && systemServerCallback != expected) {
                return;
            }
            previous = systemServerCallback;
            previousRecipient = callbackDeathRecipient;
            systemServerCallback = null;
            callbackDeathRecipient = null;
            lastPingFailure = reason == null ? "callback cleared" : reason;
        }
        if (previous != null && previousRecipient != null) {
            try {
                previous.unlinkToDeath(previousRecipient, 0);
            } catch (Throwable ignored) {
                // Binder death may already have removed the recipient.
            }
        }
        android.content.Context context = serviceContext;
        if (context != null) {
            RuntimeStatusStore.markPing(context, "FAILED", lastPingFailure);
            RuntimeDebugStore.append(context, "app", Constants.EV_RUNTIME_BINDER_BIND_FAILED,
                "runtime_service_callback", lastPingFailure);
        }
        Log.w(TAG, lastPingFailure);
    }
}
