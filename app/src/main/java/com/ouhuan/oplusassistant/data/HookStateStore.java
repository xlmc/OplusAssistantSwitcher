package com.ouhuan.oplusassistant.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.ErrorCodes;

/**
 * 最近一次 Hook 生命周期状态的轻量快照（首页「Hook 状态」来源）。
 */
public final class HookStateStore {

    private static final String PREFS = "hook_state";
    private static final String KEY_STATUS = "status";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_STRATEGY = "strategy";
    private static final String KEY_SUMMARY = "summary";
    private static final String KEY_INSTALLED = "installed";

    private HookStateStore() {
    }

    public static void update(Context context, LogEntity event) {
        if (event == null) {
            return;
        }
        if (!isLifecycleEvent(event.event)) {
            return;
        }
        boolean installed = Constants.EV_HOOK_INSTALLED.equals(event.event);
        boolean failed = event.isFailure
            || Constants.EV_HOOK_CLASS_NOT_FOUND.equals(event.event)
            || Constants.EV_HOOK_METHOD_NOT_FOUND.equals(event.event)
            || Constants.EV_HOOK_INSTALL_FAILED.equals(event.event)
            || Constants.EV_ROM_UNSUPPORTED.equals(event.event);
        SharedPreferences.Editor editor = context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATUS, orDash(event.event))
            .putLong(KEY_TIMESTAMP, event.timestamp)
            .putString(KEY_STRATEGY, orDash(event.hookStrategy))
            .putString(KEY_SUMMARY, orDash(event.exceptionSummary));
        if (installed) {
            editor.putBoolean(KEY_INSTALLED, true);
        } else if (failed) {
            editor.putBoolean(KEY_INSTALLED, false);
        }
        editor.apply();
    }

    public static String status(Context context) {
        return prefs(context).getString(KEY_STATUS, null);
    }

    public static long timestamp(Context context) {
        return prefs(context).getLong(KEY_TIMESTAMP, 0L);
    }

    public static String strategy(Context context) {
        return prefs(context).getString(KEY_STRATEGY, null);
    }

    public static String summary(Context context) {
        return prefs(context).getString(KEY_SUMMARY, null);
    }

    /** 只有明确收到 HOOK_INSTALLED 且之后没有失败里程碑时才认为 Hook 仍生效。 */
    public static boolean isInstalled(Context context) {
        return prefs(context).getBoolean(KEY_INSTALLED, false);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String orDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
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

    /** Hook 未生效时前端展示使用（开发书 12 HOOK_NOT_ACTIVE）。 */
    public static final String STATUS_NOT_ACTIVE = ErrorCodes.HOOK_NOT_ACTIVE;
}
