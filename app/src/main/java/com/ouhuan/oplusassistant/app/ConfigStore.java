package com.ouhuan.oplusassistant.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.ouhuan.oplusassistant.shared.Constants;

import io.github.libxposed.service.XposedService;

/**
 * 设置读写：Remote Preferences 是 system_server 的唯一可信配置；本地只保存用户期望值、
 * 最近一次成功镜像和同步状态。远端写入失败时，界面不会把本地期望误报成已生效。
 */
public final class ConfigStore {

    private static final String LOCAL_PREFS = "app_settings";
    private static final String KEY_SELECTED_SOURCE = "selected_source";

    private static final String KEY_DESIRED_ENABLED = "desired_module_enabled";
    private static final String KEY_DESIRED_DETAIL = "desired_detail_diagnostics";
    private static final String KEY_DESIRED_PACKAGE = "desired_selected_package";
    private static final String KEY_DESIRED_COMPONENT = "desired_selected_component";
    private static final String KEY_DESIRED_SELECTION_SET = "desired_selection_set";
    private static final String KEY_DESIRED_SOURCE = "desired_selected_source";
    private static final String KEY_DESIRED_SOURCE_SET = "desired_source_set";
    private static final String KEY_PENDING_ENABLED = "pending_enabled";
    private static final String KEY_PENDING_DETAIL = "pending_detail";
    private static final String KEY_PENDING_SELECTION = "pending_selection";
    private static final String KEY_SYNC_PENDING = "remote_sync_pending";

    private ConfigStore() {
    }

    private static SharedPreferences local(Context context) {
        return context.getApplicationContext()
            .getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
    }

    /** 只返回远端已确认且没有待同步写入的有效状态。 */
    public static boolean isModuleEnabled(Context context) {
        return status(context).effectiveEnabled();
    }

    public static String selectedPackage(Context context) {
        Status status = status(context);
        return status.remoteAvailable && !status.syncPending
            ? status.remoteSelectedPackage : status.localDesiredPackage;
    }

    public static String selectedComponent(Context context) {
        Status status = status(context);
        return status.remoteAvailable && !status.syncPending
            ? status.remoteSelectedComponent : status.localDesiredComponent;
    }

    public static boolean isDetailDiagnostics(Context context) {
        Status status = status(context);
        return status.remoteAvailable && !status.syncPending
            ? status.remoteDetailDiagnostics : status.localDesiredDetailDiagnostics;
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        local(context).edit()
            .putBoolean(KEY_DESIRED_ENABLED, enabled)
            .putBoolean(KEY_PENDING_ENABLED, true)
            .putBoolean(KEY_SYNC_PENDING, true)
            .apply();
        boolean remoteOk = putRemote(p -> p.putBoolean(Constants.KEY_MODULE_ENABLED, enabled));
        finishEnabled(context, enabled, remoteOk);
        return remoteOk;
    }

    public static boolean writeDetail(Context context, boolean detail) {
        local(context).edit()
            .putBoolean(KEY_DESIRED_DETAIL, detail)
            .putBoolean(KEY_PENDING_DETAIL, true)
            .putBoolean(KEY_SYNC_PENDING, true)
            .apply();
        boolean remoteOk = putRemote(p -> p.putBoolean(Constants.KEY_DETAIL_DIAGNOSTICS, detail));
        finishDetail(context, detail, remoteOk);
        return remoteOk;
    }

    public static boolean writeSelection(Context context, String pkg, String component,
                                         String eligibilitySource) {
        SharedPreferences.Editor editor = local(context).edit()
            .putBoolean(KEY_DESIRED_SELECTION_SET, true)
            .putBoolean(KEY_DESIRED_SOURCE_SET, true)
            .putBoolean(KEY_PENDING_SELECTION, true)
            .putBoolean(KEY_SYNC_PENDING, true);
        putNullable(editor, KEY_DESIRED_PACKAGE, pkg);
        putNullable(editor, KEY_DESIRED_COMPONENT, component);
        putNullable(editor, KEY_DESIRED_SOURCE, eligibilitySource);
        editor.apply();

        boolean remoteOk = putRemote(p -> p.putString(Constants.KEY_SELECTED_PACKAGE, pkg)
            .putString(Constants.KEY_SELECTED_COMPONENT, component));
        finishSelection(context, pkg, component, eligibilitySource, remoteOk);
        return remoteOk;
    }

    /** 资格判定依据（用户期望值，供诊断页展示）。 */
    public static String selectedSource(Context context) {
        SharedPreferences p = local(context);
        if (p.getBoolean(KEY_DESIRED_SOURCE_SET, false)) {
            return p.getString(KEY_DESIRED_SOURCE, null);
        }
        return p.getString(KEY_SELECTED_SOURCE, null);
    }

    public static boolean clearSelection(Context context) {
        SharedPreferences.Editor editor = local(context).edit()
            .putBoolean(KEY_DESIRED_SELECTION_SET, true)
            .putBoolean(KEY_DESIRED_SOURCE_SET, true)
            .putBoolean(KEY_PENDING_SELECTION, true)
            .putBoolean(KEY_SYNC_PENDING, true)
            .remove(KEY_DESIRED_PACKAGE)
            .remove(KEY_DESIRED_COMPONENT)
            .remove(KEY_DESIRED_SOURCE);
        editor.apply();

        boolean remoteOk = putRemote(p -> p.putString(Constants.KEY_SELECTED_PACKAGE, null)
            .putString(Constants.KEY_SELECTED_COMPONENT, null));
        finishSelection(context, null, null, null, remoteOk);
        return remoteOk;
    }

    /** XposedService 重新绑定后重放本地用户期望，解决 App 与 system_server 的断线窗口。 */
    public static void reconcile(Context context) {
        SharedPreferences p = local(context);
        boolean pendingEnabled = p.getBoolean(KEY_PENDING_ENABLED, false);
        boolean pendingDetail = p.getBoolean(KEY_PENDING_DETAIL, false);
        boolean pendingSelection = p.getBoolean(KEY_PENDING_SELECTION, false);
        if (!pendingEnabled && !pendingDetail && !pendingSelection) {
            return;
        }

        boolean desiredEnabled = desiredEnabled(p);
        boolean desiredDetail = desiredDetail(p);
        boolean selectionSet = p.getBoolean(KEY_DESIRED_SELECTION_SET, false);
        String desiredPackage = selectionSet ? p.getString(KEY_DESIRED_PACKAGE, null)
            : p.getString(Constants.KEY_SELECTED_PACKAGE, null);
        String desiredComponent = selectionSet ? p.getString(KEY_DESIRED_COMPONENT, null)
            : p.getString(Constants.KEY_SELECTED_COMPONENT, null);
        boolean remoteOk = putRemote(editor -> {
            if (pendingEnabled) {
                editor.putBoolean(Constants.KEY_MODULE_ENABLED, desiredEnabled);
            }
            if (pendingDetail) {
                editor.putBoolean(Constants.KEY_DETAIL_DIAGNOSTICS, desiredDetail);
            }
            if (pendingSelection) {
                editor.putString(Constants.KEY_SELECTED_PACKAGE, desiredPackage)
                    .putString(Constants.KEY_SELECTED_COMPONENT, desiredComponent);
            }
            return editor;
        });
        if (pendingEnabled) {
            finishEnabled(context, desiredEnabled, remoteOk);
        }
        if (pendingDetail) {
            finishDetail(context, desiredDetail, remoteOk);
        }
        if (pendingSelection) {
            finishSelection(context, desiredPackage, desiredComponent,
                p.getString(KEY_DESIRED_SOURCE, null), remoteOk);
        }
    }

    /** App 诊断页读取：本地期望、远端实际及是否存在同步缺口。 */
    public static Status status(Context context) {
        SharedPreferences p = local(context);
        SharedPreferences remote = remotePrefs();
        boolean remoteAvailable = remote != null;
        boolean remoteEnabled = false;
        boolean remoteDetail = false;
        String remotePackage = null;
        String remoteComponent = null;
        if (remoteAvailable) {
            try {
                remoteEnabled = remote.getBoolean(Constants.KEY_MODULE_ENABLED, false);
                remoteDetail = remote.getBoolean(Constants.KEY_DETAIL_DIAGNOSTICS, false);
                remotePackage = remote.getString(Constants.KEY_SELECTED_PACKAGE, null);
                remoteComponent = remote.getString(Constants.KEY_SELECTED_COMPONENT, null);
            } catch (Throwable ignored) {
                remoteAvailable = false;
            }
        }
        boolean syncPending = p.getBoolean(KEY_SYNC_PENDING, false)
            || p.getBoolean(KEY_PENDING_ENABLED, false)
            || p.getBoolean(KEY_PENDING_DETAIL, false)
            || p.getBoolean(KEY_PENDING_SELECTION, false);
        boolean selectionSet = p.getBoolean(KEY_DESIRED_SELECTION_SET, false);
        String localPackage = selectionSet ? p.getString(KEY_DESIRED_PACKAGE, null)
            : p.getString(Constants.KEY_SELECTED_PACKAGE, null);
        String localComponent = selectionSet ? p.getString(KEY_DESIRED_COMPONENT, null)
            : p.getString(Constants.KEY_SELECTED_COMPONENT, null);
        return new Status(
            AssistApp.service() != null,
            remoteAvailable,
            syncPending,
            desiredEnabled(p),
            remoteEnabled,
            desiredDetail(p),
            remoteDetail,
            localPackage,
            localComponent,
            remotePackage,
            remoteComponent);
    }

    private static void finishEnabled(Context context, boolean value, boolean success) {
        SharedPreferences p = local(context);
        SharedPreferences.Editor editor = p.edit();
        if (success) {
            editor.putBoolean(Constants.KEY_MODULE_ENABLED, value)
                .putBoolean(KEY_PENDING_ENABLED, false);
        } else {
            editor.putBoolean(KEY_PENDING_ENABLED, true);
        }
        editor.putBoolean(KEY_SYNC_PENDING, !success || hasOtherPending(p,
            KEY_PENDING_ENABLED));
        editor.apply();
    }

    private static void finishDetail(Context context, boolean value, boolean success) {
        SharedPreferences p = local(context);
        SharedPreferences.Editor editor = p.edit();
        if (success) {
            editor.putBoolean(Constants.KEY_DETAIL_DIAGNOSTICS, value)
                .putBoolean(KEY_PENDING_DETAIL, false);
        } else {
            editor.putBoolean(KEY_PENDING_DETAIL, true);
        }
        editor.putBoolean(KEY_SYNC_PENDING, !success || hasOtherPending(p,
            KEY_PENDING_DETAIL));
        editor.apply();
    }

    private static void finishSelection(Context context, String pkg, String component,
                                        String source, boolean success) {
        SharedPreferences p = local(context);
        SharedPreferences.Editor editor = p.edit();
        if (success) {
            putNullable(editor, Constants.KEY_SELECTED_PACKAGE, pkg);
            putNullable(editor, Constants.KEY_SELECTED_COMPONENT, component);
            putNullable(editor, KEY_SELECTED_SOURCE, source);
            editor.putBoolean(KEY_PENDING_SELECTION, false);
        } else {
            editor.putBoolean(KEY_PENDING_SELECTION, true);
        }
        editor.putBoolean(KEY_SYNC_PENDING, !success || hasOtherPending(p,
            KEY_PENDING_SELECTION));
        editor.apply();
    }

    private static boolean hasOtherPending(SharedPreferences prefs, String current) {
        return (prefs.getBoolean(KEY_PENDING_ENABLED, false) && !KEY_PENDING_ENABLED.equals(current))
            || (prefs.getBoolean(KEY_PENDING_DETAIL, false) && !KEY_PENDING_DETAIL.equals(current))
            || (prefs.getBoolean(KEY_PENDING_SELECTION, false)
                && !KEY_PENDING_SELECTION.equals(current));
    }

    private static boolean desiredEnabled(SharedPreferences p) {
        return p.contains(KEY_DESIRED_ENABLED)
            ? p.getBoolean(KEY_DESIRED_ENABLED, false)
            : p.getBoolean(Constants.KEY_MODULE_ENABLED, false);
    }

    private static boolean desiredDetail(SharedPreferences p) {
        return p.contains(KEY_DESIRED_DETAIL)
            ? p.getBoolean(KEY_DESIRED_DETAIL, false)
            : p.getBoolean(Constants.KEY_DETAIL_DIAGNOSTICS, false);
    }

    private static void putNullable(SharedPreferences.Editor editor, String key, String value) {
        if (value == null) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
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
        SharedPreferences.Editor apply(SharedPreferences.Editor editor);
    }

    /** commit() 的返回值才是 Remote Preferences 写入成功的闸门。 */
    private static boolean putRemote(EditorAction action) {
        SharedPreferences prefs = remotePrefs();
        if (prefs == null) {
            return false;
        }
        try {
            return action.apply(prefs.edit()).commit();
        } catch (Throwable t) {
            return false;
        }
    }

    public static final class Status {
        public final boolean serviceBound;
        public final boolean remoteAvailable;
        public final boolean syncPending;
        public final boolean localDesiredEnabled;
        public final boolean remoteEnabled;
        public final boolean localDesiredDetailDiagnostics;
        public final boolean remoteDetailDiagnostics;
        public final String localDesiredPackage;
        public final String localDesiredComponent;
        public final String remoteSelectedPackage;
        public final String remoteSelectedComponent;

        Status(boolean serviceBound, boolean remoteAvailable, boolean syncPending,
               boolean localDesiredEnabled, boolean remoteEnabled,
               boolean localDesiredDetailDiagnostics, boolean remoteDetailDiagnostics,
               String localDesiredPackage, String localDesiredComponent,
               String remoteSelectedPackage, String remoteSelectedComponent) {
            this.serviceBound = serviceBound;
            this.remoteAvailable = remoteAvailable;
            this.syncPending = syncPending;
            this.localDesiredEnabled = localDesiredEnabled;
            this.remoteEnabled = remoteEnabled;
            this.localDesiredDetailDiagnostics = localDesiredDetailDiagnostics;
            this.remoteDetailDiagnostics = remoteDetailDiagnostics;
            this.localDesiredPackage = localDesiredPackage;
            this.localDesiredComponent = localDesiredComponent;
            this.remoteSelectedPackage = remoteSelectedPackage;
            this.remoteSelectedComponent = remoteSelectedComponent;
        }

        public boolean effectiveEnabled() {
            return remoteAvailable && !syncPending && remoteEnabled;
        }
    }
}
