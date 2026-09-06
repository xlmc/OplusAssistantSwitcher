package com.ouhuan.oplusassistant.data;

import android.content.Context;
import android.content.SharedPreferences;

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

    private HookStateStore() {
    }

    public static void update(Context context, LogEntity event) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATUS, orDash(event.event))
            .putLong(KEY_TIMESTAMP, event.timestamp)
            .putString(KEY_STRATEGY, orDash(event.hookStrategy))
            .putString(KEY_SUMMARY, orDash(event.exceptionSummary))
            .apply();
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

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String orDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    /** Hook 未生效时前端展示使用（开发书 12 HOOK_NOT_ACTIVE）。 */
    public static final String STATUS_NOT_ACTIVE = ErrorCodes.HOOK_NOT_ACTIVE;
}
