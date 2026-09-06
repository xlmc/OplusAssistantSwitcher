package com.ouhuan.oplusassistant.xposed;

import android.util.Log;

import com.ouhuan.oplusassistant.shared.Constants;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * libxposed API 102 入口（开发书 4.1 / 6.1）。
 * 仅识别目标进程并安装 ColorOS16 策略；非 system_server 进程立即 detach。
 */
public class MainModule extends XposedModule {

    private static final String TAG = "OplusAssistantSwitcher";

    private DiagnosticReporter reporter;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        try {
            reporter = new DiagnosticReporter(this);
            if (!param.isSystemServer()) {
                log(Log.WARN, TAG, "loaded in unexpected process: " + param.getProcessName()
                    + ", detaching (scope must be system only)");
                detach();
                return;
            }
            reporter.hookEvent(Constants.EV_MODULE_LOADED,
                "process=" + param.getProcessName()
                    + ", framework=" + getFrameworkName() + " " + getFrameworkVersion()
                    + ", api=" + getApiVersion());
        } catch (Throwable t) {
            safeLog("onModuleLoaded failed", t);
        }
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        try {
            ClassLoader classLoader = param.getClassLoader();
            if (reporter == null) {
                reporter = new DiagnosticReporter(this);
            }
            reporter.hookEvent(Constants.EV_SYSTEM_SERVER_READY, null);

            ContextProvider contextProvider = new ContextProvider();
            reporter.setContextProvider(contextProvider);
            contextProvider.captureEarly();
            contextProvider.installCaptureHook(this, classLoader);

            // systemReady 前广播通道不可用：缓冲事件在此补发（开发书 8.4）
            reporter.installFlushHook(this, classLoader);

            RuntimeConfig runtimeConfig = new RuntimeConfig(this);
            ColorOS16PowerAssistantHook.install(this, classLoader, runtimeConfig,
                reporter, contextProvider, new AssistantResolver(), new AssistantLauncher());
        } catch (Throwable t) {
            safeLog("onSystemServerStarting failed", t);
        }
    }

    private void safeLog(String message, Throwable t) {
        try {
            log(Log.ERROR, TAG, message + ": " + t, t);
        } catch (Throwable ignored) {
            // 日志通道不可用时保持静默，绝不影响 system_server
        }
    }
}
