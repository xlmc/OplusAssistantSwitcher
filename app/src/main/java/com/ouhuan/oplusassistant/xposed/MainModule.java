package com.ouhuan.oplusassistant.xposed;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.util.Log;

import com.ouhuan.oplusassistant.shared.AssistantCandidate;
import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.CurrentAssistantState;
import com.ouhuan.oplusassistant.shared.ModuleVersionState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * libxposed API 102 入口（开发书 4.1 / 6.1）。
 * 仅识别目标进程并安装 ColorOS16 策略；非 system_server 进程立即 detach。
 *
 * Issue #1 评论 4：以 system_server 上下文解析「当前手机实际助手」
 * （CurrentOplusAssistant）并经状态广播回传 App；上报时机为
 * AMS.systemReady 与每次 0x3F3 路由完成后。
 */
public class MainModule extends XposedModule {

    private static final String TAG = "OplusAssistantSwitcher";

    private DiagnosticReporter reporter;
    private ContextProvider contextProvider;
    private AssistantResolver resolver;
    private String loadedModuleVersionName = "";
    private long loadedModuleVersionCode = ModuleVersionState.UNKNOWN_VERSION_CODE;
    private long moduleLoadedAt;

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
                    + ", api=" + getApiVersion()
                    + ", scope=system");
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

            contextProvider = new ContextProvider();
            reporter.setContextProvider(contextProvider);
            contextProvider.captureEarly();
            captureLoadedModuleVersion();
            reporter.hookEvent(Constants.EV_SYSTEM_SERVER_READY,
                "moduleVersion=" + moduleVersionSummary());
            contextProvider.installCaptureHook(this, classLoader);
            // Issue #1 P0 诊断点：确认 system context 是否捕获成功
            log(Log.INFO, TAG, "system context captured = " + (contextProvider.get() != null));

            // systemReady 前广播通道不可用：缓冲事件在此补发，并顺带首次上报系统助手状态
            reporter.installFlushHook(this, classLoader, this::sendAssistantStateReport);

            RuntimeConfig runtimeConfig = new RuntimeConfig(this);
            resolver = new AssistantResolver();
            ColorOS16PowerAssistantHook.install(this, classLoader, runtimeConfig,
                reporter, contextProvider, resolver, new AssistantLauncher(),
                this::sendAssistantStateReport);
        } catch (Throwable t) {
            safeLog("onSystemServerStarting failed", t);
        }
    }

    /** 解析并广播「当前手机实际助手」+ 候选列表（system_server 侧最高可信来源）。 */
    private void sendAssistantStateReport() {
        try {
            Context context = contextProvider == null ? null : contextProvider.get();
            if (context == null || resolver == null) {
                return;
            }
            if (moduleLoadedAt == 0L) {
                captureLoadedModuleVersion();
            }
            CurrentAssistantState current = resolver.readCurrentOplusAssistant(context);
            List<AssistantCandidate> candidates = resolver.scanCandidates(context);

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put(Constants.STATE_CURRENT_NAME, current.displayName);
            fields.put(Constants.STATE_CURRENT_PACKAGE, current.packageName);
            fields.put(Constants.STATE_CURRENT_COMPONENT, current.componentName);
            fields.put(Constants.STATE_CURRENT_SOURCE, current.source);
            fields.put(Constants.STATE_FROM_SYSTEM_SERVER, "true");
            fields.put(Constants.STATE_ROLE_HOLDER, current.roleHolderPackage);
            fields.put(Constants.STATE_VOICE_INTERACTION_SERVICE, current.voiceInteractionService);
            fields.put(Constants.STATE_TIMESTAMP, String.valueOf(current.timestamp));
            fields.put(Constants.STATE_MODULE_VERSION_NAME, loadedModuleVersionName);
            fields.put(Constants.STATE_MODULE_VERSION_CODE, String.valueOf(loadedModuleVersionCode));
            fields.put(Constants.STATE_MODULE_LOADED_AT, String.valueOf(moduleLoadedAt));

            List<String> entries = new ArrayList<>();
            for (AssistantCandidate candidate : candidates) {
                entries.add(candidate.label + AssistantStateStoreSep.FIELD
                    + candidate.packageName + AssistantStateStoreSep.FIELD
                    + candidate.componentName + AssistantStateStoreSep.FIELD
                    + candidate.launchMethod + AssistantStateStoreSep.FIELD
                    + candidate.eligibilitySource);
            }
            reporter.reportState(fields, entries);
            log(Log.INFO, TAG, "assistant state reported: name=" + current.displayName
                + " source=" + current.source + " candidates=" + entries.size());
        } catch (Throwable t) {
            safeLog("sendAssistantStateReport failed", t);
        }
    }

    /** 从 system_server 可用的 PackageManager 读取模块自身的实际版本。 */
    private void captureLoadedModuleVersion() {
        try {
            Context context = contextProvider == null ? null : contextProvider.get();
            ApplicationInfo applicationInfo = getModuleApplicationInfo();
            if (context == null || applicationInfo == null
                || applicationInfo.packageName == null
                || applicationInfo.packageName.isEmpty()) {
                return;
            }
            PackageInfo packageInfo = context.getPackageManager()
                .getPackageInfo(applicationInfo.packageName, 0);
            loadedModuleVersionName = packageInfo.versionName == null
                ? "" : packageInfo.versionName.trim();
            loadedModuleVersionCode = packageInfo.getLongVersionCode();
            moduleLoadedAt = System.currentTimeMillis();
        } catch (Throwable t) {
            loadedModuleVersionName = "";
            loadedModuleVersionCode = ModuleVersionState.UNKNOWN_VERSION_CODE;
            safeLog("read loaded module version failed", t);
        }
    }

    private String moduleVersionSummary() {
        String name = loadedModuleVersionName.isEmpty() ? "unknown" : loadedModuleVersionName;
        return name + "/" + loadedModuleVersionCode;
    }

    /** 与 AssistantStateStore 的序列化分隔符保持一致。 */
    private static final class AssistantStateStoreSep {
        static final String FIELD = "\u0001";
    }

    private void safeLog(String message, Throwable t) {
        try {
            log(Log.ERROR, TAG, message + ": " + t, t);
        } catch (Throwable ignored) {
            // 日志通道不可用时保持静默，绝不影响 system_server
        }
    }
}
