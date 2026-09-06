package com.ouhuan.oplusassistant.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.ouhuan.oplusassistant.shared.Constants;

import io.github.libxposed.service.XposedService;

/**
 * 设置读写：Remote Preferences 是 Hook 侧唯一可信来源；
 * 本地 SharedPreferences 仅作为镜像用于服务未连接时的界面显示。
 */
public final class ConfigStore {

    private static final String LOCAL_PREFS = "app_settings";
    private static final String KEY_SELECTED_SOURCE = "selected_source";

    private ConfigStore() {
    }

    private static SharedPreferences local(Context context) {
        return context.getApplicationContext()
            .getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isModuleEnabled(Context context) {
        SharedPreferences remote = remotePrefs();
        if (remote != null) {
            return remote.getBoolean(Constants.KEY_MODULE_ENABLED, false);
        }
        return local(context).getBoolean(Constants.KEY_MODULE_ENABLED, false);
    }

    public static String selectedPackage(Context context) {
        SharedPreferences remote = remotePrefs();
        String value = remote != null
            ? remote.getString(Constants.KEY_SELECTED_PACKAGE, null)
            : null;
        if (value == null) {
            value = local(context).getString(Constants.KEY_SELECTED_PACKAGE, null);
        }
        return value;
    }

    public static String selectedComponent(Context context) {
        SharedPreferences remote = remotePrefs();
        String value = remote != null
            ? remote.getString(Constants.KEY_SELECTED_COMPONENT, null)
            : null;
        if (value == null) {
            value = local(context).getString(Constants.KEY_SELECTED_COMPONENT, null);
        }
        return value;
    }

    public static boolean isDetailDiagnostics(Context context) {
        SharedPreferences remote = remotePrefs();
        if (remote != null) {
            return remote.getBoolean(Constants.KEY_DETAIL_DIAGNOSTICS, false);
        }
        return local(context).getBoolean(Constants.KEY_DETAIL_DIAGNOSTICS, false);
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        boolean remote = putRemote(p -> p.putBoolean(Constants.KEY_MODULE_ENABLED, enabled));
        local(context).edit().putBoolean(Constants.KEY_MODULE_ENABLED, enabled).apply();
        return remote;
    }

    public static boolean writeDetail(Context context, boolean detail) {
        boolean remote = putRemote(p -> p.putBoolean(Constants.KEY_DETAIL_DIAGNOSTICS, detail));
        local(context).edit().putBoolean(Constants.KEY_DETAIL_DIAGNOSTICS, detail).apply();
        return remote;
    }

    public static boolean writeSelection(Context context, String pkg, String component,
                                         String eligibilitySource) {
        boolean remote = putRemote(p -> p.putString(Constants.KEY_SELECTED_PACKAGE, pkg)
            .putString(Constants.KEY_SELECTED_COMPONENT, component));
        local(context).edit()
            .putString(Constants.KEY_SELECTED_PACKAGE, pkg)
            .putString(Constants.KEY_SELECTED_COMPONENT, component)
            .putString(KEY_SELECTED_SOURCE, eligibilitySource)
            .apply();
        return remote;
    }

    /** 资格判定依据（仅本地镜像，供诊断页展示）。 */
    public static String selectedSource(Context context) {
        return local(context).getString(KEY_SELECTED_SOURCE, null);
    }

    public static boolean clearSelection(Context context) {
        boolean remote = putRemote(p -> p.putString(Constants.KEY_SELECTED_PACKAGE, null)
            .putString(Constants.KEY_SELECTED_COMPONENT, null));
        local(context).edit()
            .putString(Constants.KEY_SELECTED_PACKAGE, null)
            .putString(Constants.KEY_SELECTED_COMPONENT, null)
            .putString(KEY_SELECTED_SOURCE, null)
            .apply();
        return remote;
    }

    private static SharedPreferences remotePrefs() {
        XposedService xposedService = AssistApp.service();
        if (xposedService == null) {
            return null;
        }
        try {
            return xposedService.getRemotePreferences(Constants.PREFS_GROUP);
        } catch (Throwable t) {
            return null;
        }
    }

    private interface EditorAction {
        android.content.SharedPreferences.Editor apply(android.content.SharedPreferences.Editor editor);
    }

    private static boolean putRemote(EditorAction action) {
        SharedPreferences prefs = remotePrefs();
        if (prefs == null) {
            return false;
        }
        try {
            action.apply(prefs.edit()).apply();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
