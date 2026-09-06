package com.ouhuan.oplusassistant.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.RuntimeStatusContract;

import java.util.List;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;

/**
 * App 侧运行态快照。状态来自 system_server Binder ping/push，框架服务信息来自
 * libxposed/service；UI 只读本地快照，不把一次 Binder 失败误报成 Hook 已生效。
 */
public final class RuntimeStatusStore {

    private static final String PREFS = "runtime_status";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final String KEY_FRAMEWORK_CONNECTED = "framework_connected";
    private static final String KEY_FRAMEWORK_SCOPE = "framework_scope";

    private RuntimeStatusStore() {
    }

    /** 保存 system_server 运行态 Bundle。Bundle 只允许 class-loader 中立的值。 */
    public static void apply(Context context, Bundle state) {
        if (state == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs(context).edit();
        copyString(state, editor, RuntimeStatusContract.KEY_PROCESS_NAME);
        copyString(state, editor, RuntimeStatusContract.KEY_MODULE_VERSION_NAME);
        copyLong(state, editor, RuntimeStatusContract.KEY_MODULE_VERSION_CODE,
            Constants.UNKNOWN_VERSION_CODE);
        copyLong(state, editor, RuntimeStatusContract.KEY_MODULE_LOADED_AT, 0L);
        copyString(state, editor, RuntimeStatusContract.KEY_FRAMEWORK_NAME);
        copyString(state, editor, RuntimeStatusContract.KEY_FRAMEWORK_VERSION);
        copyLong(state, editor, RuntimeStatusContract.KEY_FRAMEWORK_VERSION_CODE,
            Constants.UNKNOWN_VERSION_CODE);
        copyLong(state, editor, RuntimeStatusContract.KEY_FRAMEWORK_API, 0L);
        copyLong(state, editor, RuntimeStatusContract.KEY_FRAMEWORK_PROPERTIES, 0L);
        copyString(state, editor, RuntimeStatusContract.KEY_FRAMEWORK_TARGET_PROCESS);
        copyString(state, editor, RuntimeStatusContract.KEY_FRAMEWORK_TARGET_STATE);
        copyLong(state, editor, RuntimeStatusContract.KEY_FRAMEWORK_TARGET_VERSION_CODE,
            Constants.UNKNOWN_VERSION_CODE);
        copyLong(state, editor, RuntimeStatusContract.KEY_FRAMEWORK_TARGET_PID, 0L);
        copyString(state, editor, RuntimeStatusContract.KEY_HOOK_STRATEGY);
        copyString(state, editor, RuntimeStatusContract.KEY_HOOK_STAGE);
        copyString(state, editor, RuntimeStatusContract.KEY_HOOK_STATUS);
        copyString(state, editor, RuntimeStatusContract.KEY_CONTEXT_STATE);
        copyString(state, editor, RuntimeStatusContract.KEY_CHANNEL_STATE);
        copyString(state, editor, RuntimeStatusContract.KEY_LAST_EVENT);
        copyLong(state, editor, RuntimeStatusContract.KEY_LAST_EVENT_AT, 0L);
        copyLong(state, editor, RuntimeStatusContract.KEY_EVENT_SEQUENCE, 0L);
        copyLong(state, editor, RuntimeStatusContract.KEY_POWER_ASSIST_MATCHED_AT, 0L);
        copyString(state, editor, RuntimeStatusContract.KEY_POWER_ASSIST_STATUS);
        copyBoolean(state, editor, RuntimeStatusContract.KEY_CONFIG_KNOWN);
        copyBoolean(state, editor, RuntimeStatusContract.KEY_CONFIG_ENABLED);
        copyBoolean(state, editor, RuntimeStatusContract.KEY_CONFIG_DETAIL);
        copyString(state, editor, RuntimeStatusContract.KEY_CONFIG_SELECTED_PACKAGE);
        copyString(state, editor, RuntimeStatusContract.KEY_CONFIG_SELECTED_COMPONENT);
        copyLong(state, editor, RuntimeStatusContract.KEY_CONFIG_UPDATED_AT, 0L);
        editor.putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply();
    }

    /** 记录 libxposed/service 框架握手与当前被 Hook 目标。 */
    public static void updateFramework(Context context, XposedService service) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (service == null) {
            editor.putBoolean(KEY_FRAMEWORK_CONNECTED, false)
                .putString(RuntimeStatusContract.KEY_FRAMEWORK_NAME, "")
                .putString(RuntimeStatusContract.KEY_FRAMEWORK_VERSION, "")
                .putLong(RuntimeStatusContract.KEY_FRAMEWORK_VERSION_CODE,
                    Constants.UNKNOWN_VERSION_CODE)
                .putLong(RuntimeStatusContract.KEY_FRAMEWORK_API, 0L)
                .putLong(RuntimeStatusContract.KEY_FRAMEWORK_PROPERTIES, 0L)
                .putString(KEY_FRAMEWORK_SCOPE, "")
                .putString(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_PROCESS, "")
                .putString(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_STATE, "")
                .putLong(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_VERSION_CODE,
                    Constants.UNKNOWN_VERSION_CODE)
                .putLong(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_PID, 0L)
                .apply();
            return;
        }
        editor.putBoolean(KEY_FRAMEWORK_CONNECTED, true);
        try {
            editor.putString(RuntimeStatusContract.KEY_FRAMEWORK_NAME,
                safe(service.getFrameworkName()));
            editor.putString(RuntimeStatusContract.KEY_FRAMEWORK_VERSION,
                safe(service.getFrameworkVersion()));
            editor.putLong(RuntimeStatusContract.KEY_FRAMEWORK_VERSION_CODE,
                service.getFrameworkVersionCode());
            editor.putLong(RuntimeStatusContract.KEY_FRAMEWORK_API, service.getApiVersion());
            editor.putLong(RuntimeStatusContract.KEY_FRAMEWORK_PROPERTIES,
                service.getFrameworkProperties());
            List<String> scope = service.getScope();
            editor.putString(KEY_FRAMEWORK_SCOPE, scope == null ? "" : scope.toString());

            HookedTarget systemTarget = null;
            for (HookedTarget target : service.getRunningTargets()) {
                if (target == null) {
                    continue;
                }
                String process = target.getProcessName();
                if ("system_server".equals(process)
                    || (process != null && process.contains("system_server"))) {
                    systemTarget = target;
                    break;
                }
            }
            if (systemTarget == null) {
                editor.putString(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_PROCESS, "")
                    .putString(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_STATE, "NOT_FOUND")
                    .putLong(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_VERSION_CODE,
                        Constants.UNKNOWN_VERSION_CODE)
                    .putLong(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_PID, 0L);
            } else {
                editor.putString(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_PROCESS,
                        safe(systemTarget.getProcessName()))
                    .putString(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_STATE,
                        String.valueOf(systemTarget.getState()))
                    .putLong(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_VERSION_CODE,
                        systemTarget.getLoadedVersionCode())
                    .putLong(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_PID,
                        systemTarget.getPid());
            }
        } catch (Throwable t) {
            editor.putString(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_STATE,
                "QUERY_FAILED:" + t.getClass().getSimpleName());
        }
        editor.putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply();
    }

    public static void applyEvent(Context context, Bundle event) {
        if (event == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs(context).edit();
        copyString(event, editor, RuntimeStatusContract.KEY_LAST_EVENT,
            Constants.FIELD_EVENT);
        copyLong(event, editor, RuntimeStatusContract.KEY_LAST_EVENT_AT,
            Constants.FIELD_TIMESTAMP, 0L);
        editor.putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply();
    }

    public static Snapshot current(Context context) {
        SharedPreferences p = prefs(context);
        return new Snapshot(
            p.getBoolean(KEY_FRAMEWORK_CONNECTED, false),
            p.getString(KEY_FRAMEWORK_SCOPE, ""),
            p.getString(RuntimeStatusContract.KEY_PROCESS_NAME, ""),
            p.getString(RuntimeStatusContract.KEY_MODULE_VERSION_NAME, ""),
            p.getLong(RuntimeStatusContract.KEY_MODULE_VERSION_CODE,
                Constants.UNKNOWN_VERSION_CODE),
            p.getLong(RuntimeStatusContract.KEY_MODULE_LOADED_AT, 0L),
            p.getString(RuntimeStatusContract.KEY_FRAMEWORK_NAME, ""),
            p.getString(RuntimeStatusContract.KEY_FRAMEWORK_VERSION, ""),
            p.getLong(RuntimeStatusContract.KEY_FRAMEWORK_VERSION_CODE,
                Constants.UNKNOWN_VERSION_CODE),
            p.getLong(RuntimeStatusContract.KEY_FRAMEWORK_API, 0L),
            p.getLong(RuntimeStatusContract.KEY_FRAMEWORK_PROPERTIES, 0L),
            p.getString(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_PROCESS, ""),
            p.getString(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_STATE, ""),
            p.getLong(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_VERSION_CODE,
                Constants.UNKNOWN_VERSION_CODE),
            p.getLong(RuntimeStatusContract.KEY_FRAMEWORK_TARGET_PID, 0L),
            p.getString(RuntimeStatusContract.KEY_HOOK_STRATEGY, ""),
            p.getString(RuntimeStatusContract.KEY_HOOK_STAGE, ""),
            p.getString(RuntimeStatusContract.KEY_HOOK_STATUS, ""),
            p.getString(RuntimeStatusContract.KEY_CONTEXT_STATE, ""),
            p.getString(RuntimeStatusContract.KEY_CHANNEL_STATE, ""),
            p.getString(RuntimeStatusContract.KEY_LAST_EVENT, ""),
            p.getLong(RuntimeStatusContract.KEY_LAST_EVENT_AT, 0L),
            p.getLong(RuntimeStatusContract.KEY_EVENT_SEQUENCE, 0L),
            p.getLong(RuntimeStatusContract.KEY_POWER_ASSIST_MATCHED_AT, 0L),
            p.getString(RuntimeStatusContract.KEY_POWER_ASSIST_STATUS, ""),
            p.getBoolean(RuntimeStatusContract.KEY_CONFIG_KNOWN, false),
            p.getBoolean(RuntimeStatusContract.KEY_CONFIG_ENABLED, false),
            p.getBoolean(RuntimeStatusContract.KEY_CONFIG_DETAIL, false),
            p.getString(RuntimeStatusContract.KEY_CONFIG_SELECTED_PACKAGE, ""),
            p.getString(RuntimeStatusContract.KEY_CONFIG_SELECTED_COMPONENT, ""),
            p.getLong(RuntimeStatusContract.KEY_CONFIG_UPDATED_AT, 0L),
            p.getLong(KEY_UPDATED_AT, 0L));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void copyString(Bundle source, SharedPreferences.Editor editor, String key) {
        copyString(source, editor, key, key);
    }

    private static void copyString(Bundle source, SharedPreferences.Editor editor,
                                   String targetKey, String sourceKey) {
        if (source.containsKey(sourceKey)) {
            editor.putString(targetKey, safe(source.getString(sourceKey, "")));
        }
    }

    private static void copyLong(Bundle source, SharedPreferences.Editor editor,
                                 String key, long fallback) {
        copyLong(source, editor, key, key, fallback);
    }

    private static void copyLong(Bundle source, SharedPreferences.Editor editor,
                                 String targetKey, String sourceKey, long fallback) {
        if (!source.containsKey(sourceKey)) {
            return;
        }
        Object value = source.get(sourceKey);
        if (value instanceof Long) {
            editor.putLong(targetKey, (Long) value);
        } else if (value instanceof Integer) {
            editor.putLong(targetKey, ((Integer) value).longValue());
        } else {
            try {
                editor.putLong(targetKey, Long.parseLong(String.valueOf(value)));
            } catch (NumberFormatException ignored) {
                editor.putLong(targetKey, fallback);
            }
        }
    }

    private static void copyBoolean(Bundle source, SharedPreferences.Editor editor, String key) {
        if (source.containsKey(key)) {
            editor.putBoolean(key, source.getBoolean(key, false));
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class Snapshot {
        public final boolean frameworkConnected;
        public final String frameworkScope;
        public final String processName;
        public final String moduleVersionName;
        public final long moduleVersionCode;
        public final long moduleLoadedAt;
        public final String frameworkName;
        public final String frameworkVersion;
        public final long frameworkVersionCode;
        public final long frameworkApi;
        public final long frameworkProperties;
        public final String targetProcess;
        public final String targetState;
        public final long targetLoadedVersionCode;
        public final long targetPid;
        public final String hookStrategy;
        public final String hookStage;
        public final String hookStatus;
        public final String contextState;
        public final String channelState;
        public final String lastEvent;
        public final long lastEventAt;
        public final long eventSequence;
        public final long powerAssistMatchedAt;
        public final String powerAssistStatus;
        public final boolean configKnown;
        public final boolean configEnabled;
        public final boolean configDetail;
        public final String configSelectedPackage;
        public final String configSelectedComponent;
        public final long configUpdatedAt;
        public final long updatedAt;

        Snapshot(boolean frameworkConnected, String frameworkScope, String processName,
                 String moduleVersionName, long moduleVersionCode, long moduleLoadedAt,
                 String frameworkName, String frameworkVersion, long frameworkVersionCode,
                 long frameworkApi, long frameworkProperties, String targetProcess,
                 String targetState, long targetLoadedVersionCode, long targetPid,
                 String hookStrategy, String hookStage, String hookStatus, String contextState,
                 String channelState, String lastEvent, long lastEventAt, long eventSequence,
                 long powerAssistMatchedAt, String powerAssistStatus, boolean configKnown,
                 boolean configEnabled, boolean configDetail, String configSelectedPackage,
                 String configSelectedComponent, long configUpdatedAt, long updatedAt) {
            this.frameworkConnected = frameworkConnected;
            this.frameworkScope = frameworkScope;
            this.processName = processName;
            this.moduleVersionName = moduleVersionName;
            this.moduleVersionCode = moduleVersionCode;
            this.moduleLoadedAt = moduleLoadedAt;
            this.frameworkName = frameworkName;
            this.frameworkVersion = frameworkVersion;
            this.frameworkVersionCode = frameworkVersionCode;
            this.frameworkApi = frameworkApi;
            this.frameworkProperties = frameworkProperties;
            this.targetProcess = targetProcess;
            this.targetState = targetState;
            this.targetLoadedVersionCode = targetLoadedVersionCode;
            this.targetPid = targetPid;
            this.hookStrategy = hookStrategy;
            this.hookStage = hookStage;
            this.hookStatus = hookStatus;
            this.contextState = contextState;
            this.channelState = channelState;
            this.lastEvent = lastEvent;
            this.lastEventAt = lastEventAt;
            this.eventSequence = eventSequence;
            this.powerAssistMatchedAt = powerAssistMatchedAt;
            this.powerAssistStatus = powerAssistStatus;
            this.configKnown = configKnown;
            this.configEnabled = configEnabled;
            this.configDetail = configDetail;
            this.configSelectedPackage = configSelectedPackage;
            this.configSelectedComponent = configSelectedComponent;
            this.configUpdatedAt = configUpdatedAt;
            this.updatedAt = updatedAt;
        }
    }
}
