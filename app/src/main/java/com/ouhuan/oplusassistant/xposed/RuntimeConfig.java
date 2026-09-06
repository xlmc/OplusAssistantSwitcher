package com.ouhuan.oplusassistant.xposed;

import android.content.Context;
import android.content.SharedPreferences;

import com.ouhuan.oplusassistant.shared.Constants;

import io.github.libxposed.api.XposedInterface;

import java.util.Locale;

/**
 * 从 Remote Preferences 同步设置并在 system_server 内缓存（开发书 4.1）。
 * 每次触发仅做一次轻量读取并缓存快照，避免热路径反复 Binder/磁盘读取。
 * 偏好读取失败时返回 null，调用方按「模块未启用」处理，不做替换（开发书 6.3）。
 */
public final class RuntimeConfig {

    /** 不可变配置快照。 */
    public static final class Snapshot {
        public final boolean enabled;
        public final boolean detailDiagnostics;
        public final String selectedPackage;
        public final String selectedComponent;

        Snapshot(boolean enabled, boolean detailDiagnostics, String selectedPackage, String selectedComponent) {
            this.enabled = enabled;
            this.detailDiagnostics = detailDiagnostics;
            this.selectedPackage = selectedPackage;
            this.selectedComponent = selectedComponent;
        }

        public boolean hasSelection() {
            return selectedPackage != null && !selectedPackage.trim().isEmpty();
        }
    }

    private final XposedInterface xposed;
    private volatile Snapshot last;

    public RuntimeConfig(XposedInterface xposed) {
        this.xposed = xposed;
    }

    /** 触发时调用：刷新并返回快照；读取失败返回 null（视为未启用）。 */
    public Snapshot refresh() {
        Snapshot snapshot = readSnapshot();
        if (snapshot != null) {
            last = snapshot;
        }
        return snapshot;
    }

    /** 上一次成功读取的缓存；可能为 null。 */
    public Snapshot cached() {
        return last;
    }

    private Snapshot readSnapshot() {
        if (xposed == null) {
            return null;
        }
        try {
            SharedPreferences prefs = xposed.getRemotePreferences(Constants.PREFS_GROUP);
            if (prefs == null) {
                return null;
            }
            boolean enabled = prefs.getBoolean(Constants.KEY_MODULE_ENABLED, false);
            boolean detail = prefs.getBoolean(Constants.KEY_DETAIL_DIAGNOSTICS, false);
            String pkg = prefs.getString(Constants.KEY_SELECTED_PACKAGE, null);
            String comp = prefs.getString(Constants.KEY_SELECTED_COMPONENT, null);
            return new Snapshot(enabled, detail, pkg, comp);
        } catch (Throwable t) {
            safeLog("RuntimeConfig read failed: " + t);
            return null;
        }
    }

    private void safeLog(String msg) {
        try {
            if (xposed != null) {
                xposed.log(android.util.Log.WARN, Constants.MODULE_PACKAGE, msg);
            }
        } catch (Throwable ignored) {
            // 日志通道不可用时保持静默
        }
    }

    @Override
    public String toString() {
        Snapshot s = last;
        return s == null ? "RuntimeConfig{no snapshot}" : String.format(Locale.US,
            "RuntimeConfig{enabled=%b, pkg=%s}", s.enabled, s.selectedPackage);
    }

    /** 供 Hook 侧判断当前进程上下文是否可用（仅内部使用）。 */
    public static boolean isContextUsable(Context context) {
        return context != null;
    }
}
