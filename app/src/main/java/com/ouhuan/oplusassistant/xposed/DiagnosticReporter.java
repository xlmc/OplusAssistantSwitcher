package com.ouhuan.oplusassistant.xposed;

import android.content.Context;
import android.content.Intent;

import com.ouhuan.oplusassistant.shared.Constants;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/**
 * 轻量日志事件的缓冲与异步上报（开发书 8.4）。
 * system_server 触发热路径内绝不写 Room/SQLite：事件要么立即广播给模块 App，
 * 要么进入内存缓冲，待 ActivityManagerService.systemReady 后补发。
 * 广播不可达时降级写入 Xposed 模块日志。
 */
public final class DiagnosticReporter {

    private static final int BUFFER_LIMIT = 64;

    private final XposedInterface xposed;
    private final String modulePackage;
    private final String androidVersion;
    private final String colorOsVersion;
    private final String deviceModel;
    private final List<com.ouhuan.oplusassistant.shared.LogEvent> pending = new ArrayList<>();

    private volatile ContextProvider contextProvider;
    private volatile String hookStatus = "-";

    public DiagnosticReporter(XposedInterface xposed) {
        this.xposed = xposed;
        String pkg;
        try {
            pkg = xposed.getModuleApplicationInfo().packageName;
        } catch (Throwable t) {
            pkg = Constants.MODULE_PACKAGE;
        }
        this.modulePackage = pkg;
        this.androidVersion = "Android " + android.os.Build.VERSION.RELEASE
            + " (API " + android.os.Build.VERSION.SDK_INT + ")";
        this.deviceModel = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
        this.colorOsVersion = readColorOsVersion();
    }

    public void setContextProvider(ContextProvider provider) {
        this.contextProvider = provider;
    }

    /** Hook 生命周期日志（开发书 8.2 Hook 日志）。 */
    public void hookEvent(String event, String summary) {
        hookStatus = event;
        com.ouhuan.oplusassistant.shared.LogEvent e =
            new com.ouhuan.oplusassistant.shared.LogEvent();
        e.kind = Constants.KIND_HOOK;
        e.event = event;
        e.hookStrategy = Constants.HOOK_STRATEGY_COLOROS16;
        e.hookStatus = event;
        e.exceptionSummary = summary == null || summary.isEmpty() ? "" : summary;
        report(e);
    }

    /** 调用链路日志（开发书 8.2 调用日志）。调用方构造事件，此处补全公共字段。 */
    public void report(com.ouhuan.oplusassistant.shared.LogEvent event) {
        if (event == null) {
            return;
        }
        if (event.timestamp == null || event.timestamp.isEmpty()) {
            event.timestamp = String.valueOf(System.currentTimeMillis());
        }
        if (event.androidVersion == null || event.androidVersion.isEmpty()) {
            event.androidVersion = androidVersion;
        }
        if (event.colorOsVersion == null || event.colorOsVersion.isEmpty()) {
            event.colorOsVersion = colorOsVersion;
        }
        if (event.deviceModel == null || event.deviceModel.isEmpty()) {
            event.deviceModel = deviceModel;
        }
        if (event.hookStrategy == null || event.hookStrategy.isEmpty()) {
            event.hookStrategy = Constants.HOOK_STRATEGY_COLOROS16;
        }
        if (event.hookStatus == null || event.hookStatus.isEmpty()) {
            event.hookStatus = hookStatus;
        }
        send(event);
    }

    private void send(com.ouhuan.oplusassistant.shared.LogEvent event) {
        Context context = contextProvider == null ? null : contextProvider.get();
        if (context == null) {
            buffer(event);
            return;
        }
        try {
            Intent intent = new Intent(Constants.ACTION_LOG_EVENT);
            intent.setPackage(modulePackage);
            for (java.util.Map.Entry<String, String> entry : event.toMap().entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
            context.sendBroadcast(intent);
        } catch (Throwable t) {
            buffer(event);
        }
    }

    private synchronized void buffer(com.ouhuan.oplusassistant.shared.LogEvent event) {
        if (pending.size() >= BUFFER_LIMIT) {
            pending.remove(0);
        }
        pending.add(event);
        fallbackLog(event);
    }

    private void fallbackLog(com.ouhuan.oplusassistant.shared.LogEvent event) {
        try {
            if (xposed != null) {
                xposed.log(android.util.Log.INFO, modulePackage, event.toReadableText());
            }
        } catch (Throwable ignored) {
            // 日志通道不可用时保持静默
        }
    }

    /** AMS.systemReady 后补发缓冲事件，并触发一次性系统助手状态上报；此时广播通道已可用。 */
    public void installFlushHook(XposedInterface xposed, ClassLoader classLoader,
                                 Runnable onSystemReady) {
        try {
            Class<?> ams = Class.forName("com.android.server.am.ActivityManagerService",
                false, classLoader);
            for (Method method : ams.getDeclaredMethods()) {
                if ("systemReady".equals(method.getName())) {
                    method.setAccessible(true);
                    xposed.hook(method).intercept(chain -> {
                        chain.proceed();
                        flush();
                        if (onSystemReady != null) {
                            try {
                                onSystemReady.run();
                            } catch (Throwable t) {
                                safeLog("onSystemReady callback failed: " + t);
                            }
                        }
                        return null;
                    });
                }
            }
        } catch (Throwable t) {
            safeLog("installFlushHook failed: " + t);
        }
    }

    /**
     * 状态上报（Issue #1 评论 4）：把 system_server 侧解析的
     * CurrentOplusAssistant 与候选列表广播给模块 App。
     * 无上下文或发送失败仅记录 Xposed 日志，状态不是日志事件、不进缓冲。
     */
    public void reportState(java.util.Map<String, String> fields,
                            java.util.List<String> candidateEntries) {
        Context context = contextProvider == null ? null : contextProvider.get();
        if (context == null) {
            safeLog("reportState skipped: no system context");
            return;
        }
        try {
            Intent intent = new Intent(Constants.ACTION_STATE_REPORT);
            intent.setPackage(modulePackage);
            if (fields != null) {
                for (java.util.Map.Entry<String, String> entry : fields.entrySet()) {
                    intent.putExtra(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
                }
            }
            if (candidateEntries != null) {
                intent.putStringArrayListExtra(Constants.STATE_CANDIDATES,
                    new ArrayList<>(candidateEntries));
            }
            context.sendBroadcast(intent);
        } catch (Throwable t) {
            safeLog("reportState failed: " + t);
        }
    }

    public synchronized void flush() {
        if (pending.isEmpty()) {
            return;
        }
        List<com.ouhuan.oplusassistant.shared.LogEvent> drained = new ArrayList<>(pending);
        pending.clear();
        for (com.ouhuan.oplusassistant.shared.LogEvent event : drained) {
            send(event);
        }
    }

    private void safeLog(String msg) {
        try {
            if (xposed != null) {
                xposed.log(android.util.Log.WARN, modulePackage, msg);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String readColorOsVersion() {
        String[] keys = {
            "ro.build.version.oplusrom",
            "ro.oplus.version",
            "ro.vendor.oplus.version"
        };
        for (String key : keys) {
            String value = systemProperty(key);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "-";
    }

    private static String systemProperty(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object value = sp.getMethod("get", String.class).invoke(null, key);
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Throwable t) {
            return "";
        }
    }
}
