package com.ouhuan.oplusassistant.xposed;

import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * 在 system_server 内捕获可用的系统 Context（开发书 3 参考内容中的「Context 捕获」）。
 * 优先反射 ActivityThread#getSystemContext，并 Hook SystemServer#createSystemContext
 * 兜底捕获，任何失败都只记录、不外抛。
 */
public final class ContextProvider {

    private volatile Context context;
    private volatile Runnable readyListener;
    private boolean readyNotified;

    /** 设置 context 就绪回调；若 context 已经捕获则立即补发一次。 */
    public void setReadyListener(Runnable listener) {
        boolean notify;
        synchronized (this) {
            readyListener = listener;
            notify = context != null && listener != null && !readyNotified;
            if (notify) {
                readyNotified = true;
            }
        }
        if (notify) {
            runReadyListener(listener);
        }
    }

    /** 尽早尝试反射获取 system context（onSystemServerStarting 阶段调用）。 */
    public void captureEarly() {
        if (context != null) {
            return;
        }
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            if (thread != null) {
                Object ctx = at.getMethod("getSystemContext").invoke(thread);
                if (ctx instanceof Context) {
                    setContext((Context) ctx);
                }
            }
        } catch (Throwable ignored) {
            // 兜底捕获会通过 createSystemContext Hook 继续
        }
    }

    /** Hook SystemServer#createSystemContext，在系统创建 context 后捕获。 */
    public void installCaptureHook(XposedInterface xposed, ClassLoader classLoader) {
        try {
            Class<?> systemServer = Class.forName("com.android.server.SystemServer",
                false, classLoader);
            Method method = systemServer.getDeclaredMethod("createSystemContext");
            method.setAccessible(true);
            xposed.hook(method).intercept(chain -> {
                chain.proceed();
                captureFromServer(chain.getThisObject());
                return null;
            });
        } catch (Throwable ignored) {
            // 捕获失败仅影响日志广播与启动能力，交由运行期 get() 再试
        }
    }

    private void captureFromServer(Object systemServerInstance) {
        if (systemServerInstance == null || context != null) {
            return;
        }
        try {
            Field field = systemServerInstance.getClass().getDeclaredField("mSystemContext");
            field.setAccessible(true);
            Object ctx = field.get(systemServerInstance);
            if (ctx instanceof Context) {
                setContext((Context) ctx);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 获取缓存的 system context；为空时再尝试一次反射捕获。 */
    public Context get() {
        if (context != null) {
            return context;
        }
        captureEarly();
        return context;
    }

    private void setContext(Context value) {
        Runnable listener = null;
        synchronized (this) {
            if (context != null || value == null) {
                return;
            }
            context = value;
            if (readyListener != null && !readyNotified) {
                readyNotified = true;
                listener = readyListener;
            }
        }
        if (listener != null) {
            runReadyListener(listener);
        }
    }

    private void runReadyListener(Runnable listener) {
        try {
            listener.run();
        } catch (Throwable ignored) {
            // context 回调失败不影响 system_server 启动。
        }
    }
}
