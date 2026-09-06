package com.ouhuan.oplusassistant.app;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;

import com.ouhuan.oplusassistant.data.AppExecutors;
import com.ouhuan.oplusassistant.data.AssistantStateStore;
import com.ouhuan.oplusassistant.data.HookStateStore;
import com.ouhuan.oplusassistant.data.LogDb;
import com.ouhuan.oplusassistant.data.LogEntity;
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

    private static volatile IBinder systemServerCallback;

    private final IBinder binder = new StatusBinder();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        systemServerCallback = null;
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
            return null;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(RuntimeStatusContract.CALLBACK_DESCRIPTOR);
            callback.transact(RuntimeStatusContract.TRANSACTION_PING, data, reply, 0);
            reply.readException();
            Bundle result = reply.readBundle(RuntimeStatusService.class.getClassLoader());
            if (result != null) {
                result.setClassLoader(RuntimeStatusService.class.getClassLoader());
            }
            return result;
        } catch (Throwable ignored) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
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
            // 诊断数据失败不影响 App 或 system_server。
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
            // 诊断落库失败不反向影响 system_server 热路径。
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
                    systemServerCallback = data.readStrongBinder();
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

        private void enforceSystemCaller() {
            if (Binder.getCallingUid() != Process.SYSTEM_UID) {
                throw new SecurityException("RuntimeStatusService accepts system uid only");
            }
        }
    }
}
