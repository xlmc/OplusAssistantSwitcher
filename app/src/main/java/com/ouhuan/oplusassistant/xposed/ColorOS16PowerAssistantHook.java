package com.ouhuan.oplusassistant.xposed;

import android.content.Context;
import android.os.Message;
import android.os.Build;

import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.ErrorCodes;
import com.ouhuan.oplusassistant.shared.LaunchResult;
import com.ouhuan.oplusassistant.shared.LogEvent;
import com.ouhuan.oplusassistant.shared.SystemAssistantState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * ColorOS16 策略：仅匹配 0.5 秒电源键助手唤醒事件（0x3F3）并进入路由（开发书 3.1 / 4.1）。
 * 不负责 UI / 数据库；Hook 安装失败一律退化为「不接管」，绝不让 system_server 崩溃。
 */
public final class ColorOS16PowerAssistantHook {

    /** ROM 识别结果。 */
    public static final class RomPolicy {
        public final boolean supported;
        public final String romVersion;

        RomPolicy(boolean supported, String romVersion) {
            this.supported = supported;
            this.romVersion = romVersion;
        }
    }

    private final XposedInterface xposed;
    private final RuntimeConfig runtimeConfig;
    private final DiagnosticReporter reporter;
    private final ContextProvider contextProvider;
    private final AssistantResolver resolver;
    private final AssistantLauncher launcher;

    private ColorOS16PowerAssistantHook(XposedInterface xposed,
                                        RuntimeConfig runtimeConfig,
                                        DiagnosticReporter reporter,
                                        ContextProvider contextProvider,
                                        AssistantResolver resolver,
                                        AssistantLauncher launcher) {
        this.xposed = xposed;
        this.runtimeConfig = runtimeConfig;
        this.reporter = reporter;
        this.contextProvider = contextProvider;
        this.resolver = resolver;
        this.launcher = launcher;
    }

    /** 识别 ROM：OPlus 属性或厂商名命中才安装 Hook，否则记录 ROM_UNSUPPORTED（开发书 3.1）。 */
    public static RomPolicy detectRom() {
        String[] keys = {
            "ro.build.version.oplusrom",
            "ro.oplus.version",
            "ro.vendor.oplus.version"
        };
        for (String key : keys) {
            String value = systemProperty(key);
            if (!value.isEmpty()) {
                return new RomPolicy(true, value);
            }
        }
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String normalized = manufacturer.toLowerCase(java.util.Locale.US);
        if (normalized.contains("oppo") || normalized.contains("oneplus")
            || normalized.contains("realme")) {
            // OxygenOS / realme UI 与 ColorOS 16 同源但实现差异未实机验证，仅允许尝试
            return new RomPolicy(true, manufacturer);
        }
        return new RomPolicy(false, manufacturer);
    }

    /** 在 system_server 安装 0x3F3 Hook；任何失败均不外抛。 */
    public static void install(XposedInterface xposed,
                               ClassLoader classLoader,
                               RuntimeConfig runtimeConfig,
                               DiagnosticReporter reporter,
                               ContextProvider contextProvider,
                               AssistantResolver resolver,
                               AssistantLauncher launcher) {
        RomPolicy rom = detectRom();
        if (!rom.supported) {
            reporter.hookEvent(Constants.EV_ROM_UNSUPPORTED,
                "not an OPlus ROM: " + rom.romVersion);
            return;
        }

        ColorOS16PowerAssistantHook hook = new ColorOS16PowerAssistantHook(
            xposed, runtimeConfig, reporter, contextProvider, resolver, launcher);
        try {
            Class<?> owner = Class.forName(Constants.HOOK_CLASS, false, classLoader);
            reporter.hookEvent(Constants.EV_HOOK_CLASS_FOUND, Constants.HOOK_CLASS);

            Method method = owner.getDeclaredMethod(Constants.HOOK_METHOD, Message.class);
            method.setAccessible(true);
            reporter.hookEvent(Constants.EV_HOOK_METHOD_FOUND,
                Constants.HOOK_CLASS + "." + Constants.HOOK_METHOD);

            xposed.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> hook.onHandleMessage(chain));
            reporter.hookEvent(Constants.EV_HOOK_INSTALLED,
                Constants.HOOK_CLASS + "." + Constants.HOOK_METHOD
                    + " what=0x" + Integer.toHexString(Constants.MSG_POWER_ASSIST_0X3F3));
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // 厂商类/方法随 ROM 更新变化：不接管，仅记录（开发书 6.3 / 14）
            reporter.hookEvent(Constants.EV_HOOK_TARGET_NOT_FOUND,
                e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (Throwable t) {
            reporter.hookEvent(Constants.EV_HOOK_FAILED,
                t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private Object onHandleMessage(XposedInterface.Chain chain) throws Throwable {
        Object arg = chain.getArg(0);
        if (!(arg instanceof Message)) {
            return chain.proceed();
        }
        Message message = (Message) arg;
        if (message.what != Constants.MSG_POWER_ASSIST_0X3F3) {
            return chain.proceed();
        }

        // 命中 0.5 秒助手唤醒事件（开发书 2.2 调用流程）
        RuntimeConfig.Snapshot snapshot = runtimeConfig.refresh();
        if (snapshot == null || !snapshot.enabled) {
            // 模块未启用（或偏好读取失败）：执行系统原逻辑
            return chain.proceed();
        }

        // 已启用：消费原调用，不执行小布、不提示；失败静默终止并记录
        try {
            route(snapshot);
        } catch (Throwable t) {
            reportTerminal(snapshot, Constants.EV_LAUNCH_EXCEPTION,
                LaunchResult.LAUNCH_EXCEPTION, ErrorCodes.UNKNOWN_ERROR,
                summarize(t, snapshot.detailDiagnostics));
        }
        return null;
    }

    /** 路由主流程：解析目标 → 发起启动 → 记录结果（开发书 2.2 / 7）。 */
    private void route(RuntimeConfig.Snapshot snapshot) {
        LogEvent triggered = new LogEvent();
        triggered.kind = Constants.KIND_CALL;
        triggered.event = Constants.EV_POWER_ASSIST_TRIGGERED;
        triggered.trigger = Constants.TRIGGER_POWER_ASSIST_0X3F3;
        triggered.selectedAssistant = snapshot.selectedPackage;
        triggered.targetPackage = snapshot.selectedPackage;
        triggered.targetComponent = snapshot.selectedComponent;
        Context context = contextProvider.get();
        SystemAssistantState systemDefault = context == null
            ? null
            : resolver.readSystemDefault(context);
        triggered.systemDefaultAssistant = systemDefault == null
            ? "-"
            : systemDefault.describe();
        reporter.report(triggered);

        if (context == null) {
            reportTerminal(snapshot, Constants.EV_RESOLVE_FAILED,
                LaunchResult.RESOLVE_FAILED, ErrorCodes.RESOLVE_FAILED,
                "system context unavailable");
            return;
        }

        AssistantResolver.ResolveOutcome outcome =
            resolver.resolve(context, snapshot, systemDefault);
        if (!outcome.ok()) {
            reportTerminal(snapshot, failureEventFor(outcome.result), outcome.result,
                outcome.failureCode, outcome.summary);
            return;
        }

        LogEvent resolved = new LogEvent();
        resolved.kind = Constants.KIND_CALL;
        resolved.event = Constants.EV_TARGET_RESOLVED;
        resolved.trigger = Constants.TRIGGER_POWER_ASSIST_0X3F3;
        resolved.systemDefaultAssistant = systemDefault.describe();
        resolved.selectedAssistant = snapshot.selectedPackage;
        resolved.targetPackage = snapshot.selectedPackage;
        resolved.targetComponent = outcome.target.componentName;
        resolved.launchMethod = outcome.target.launchMethod;
        reporter.report(resolved);

        LogEvent requested = new LogEvent();
        requested.kind = Constants.KIND_CALL;
        requested.event = Constants.EV_LAUNCH_REQUESTED;
        requested.trigger = Constants.TRIGGER_POWER_ASSIST_0X3F3;
        requested.systemDefaultAssistant = systemDefault.describe();
        requested.selectedAssistant = snapshot.selectedPackage;
        requested.targetPackage = snapshot.selectedPackage;
        requested.targetComponent = outcome.target.componentName;
        requested.launchMethod = outcome.target.launchMethod;
        reporter.report(requested);

        AssistantLauncher.LaunchOutcome launch =
            launcher.launch(context, outcome.target);
        if (launch.result == LaunchResult.SUCCESS) {
            LogEvent accepted = new LogEvent();
            accepted.kind = Constants.KIND_CALL;
            accepted.event = Constants.EV_LAUNCH_ACCEPTED;
            accepted.trigger = Constants.TRIGGER_POWER_ASSIST_0X3F3;
            accepted.systemDefaultAssistant = systemDefault.describe();
            accepted.selectedAssistant = snapshot.selectedPackage;
            accepted.targetPackage = snapshot.selectedPackage;
            accepted.targetComponent = outcome.target.componentName;
            accepted.launchMethod = outcome.target.launchMethod;
            accepted.result = LaunchResult.SUCCESS.name();
            reporter.report(accepted);
            return;
        }

        String event = launch.result == LaunchResult.SECURITY_EXCEPTION
            ? Constants.EV_SECURITY_EXCEPTION
            : Constants.EV_LAUNCH_EXCEPTION;
        reportTerminal(snapshot, event, launch.result, mapFailureCode(launch.result),
            launch.summary);
    }

    private void reportTerminal(RuntimeConfig.Snapshot snapshot, String event,
                                LaunchResult result, String failureCode, String summary) {
        LogEvent terminal = new LogEvent();
        terminal.kind = Constants.KIND_CALL;
        terminal.event = event;
        terminal.trigger = Constants.TRIGGER_POWER_ASSIST_0X3F3;
        if (snapshot != null) {
            terminal.selectedAssistant = snapshot.selectedPackage;
            terminal.targetPackage = snapshot.selectedPackage;
            terminal.targetComponent = snapshot.selectedComponent;
        }
        terminal.result = result.name();
        terminal.failureCode = failureCode == null || failureCode.isEmpty()
            ? result.name() : failureCode;
        terminal.exceptionSummary = summary == null || summary.isEmpty() ? "" : summary;
        reporter.report(terminal);
    }

    private static String failureEventFor(LaunchResult result) {
        if (result == LaunchResult.TARGET_NOT_FOUND) {
            return Constants.EV_TARGET_NOT_FOUND;
        }
        return Constants.EV_RESOLVE_FAILED;
    }

    private static String mapFailureCode(LaunchResult result) {
        switch (result) {
            case SECURITY_EXCEPTION:
                return ErrorCodes.SECURITY_EXCEPTION;
            case BACKGROUND_START_DENIED:
                return ErrorCodes.BACKGROUND_START_DENIED;
            case LAUNCH_EXCEPTION:
                return ErrorCodes.LAUNCH_EXCEPTION;
            default:
                return ErrorCodes.UNKNOWN_ERROR;
        }
    }

    /** 详细诊断模式记录异常类型与堆栈摘要；普通模式仅记录消息（开发书 8.3）。 */
    private String summarize(Throwable t, boolean detail) {
        if (t == null) {
            return "";
        }
        String type = t.getClass().getSimpleName();
        String message = t.getMessage() == null ? t.toString() : t.getMessage();
        if (!detail) {
            return type + ": " + message;
        }
        StringBuilder sb = new StringBuilder(type).append(": ").append(message);
        StackTraceElement[] frames = t.getStackTrace();
        int limit = Math.min(frames.length, 5);
        for (int i = 0; i < limit; i++) {
            sb.append("\n  at ").append(frames[i]);
        }
        return sb.toString();
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
