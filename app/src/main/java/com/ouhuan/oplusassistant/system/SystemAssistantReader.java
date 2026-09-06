package com.ouhuan.oplusassistant.system;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import com.ouhuan.oplusassistant.shared.RoleHolders;
import com.ouhuan.oplusassistant.shared.SystemAssistantState;

/**
 * 首页「当前系统默认助手」读取（开发书 5.1；Issue #1 第四条）。
 * 与 Hook 状态完全解耦：无论模块是否生效，都独立读取系统实时状态。
 * 读取顺序：ROLE_ASSISTANT 持有者 → VoiceInteractionService → 电源键助手组件。
 * 三者皆不可得时明确显示「未设置或无法识别」，而不是跟随 Hook 状态。
 */
public final class SystemAssistantReader {

    public SystemAssistantState read(Context context) {
        String roleHolder = null;
        try {
            RoleManager roleManager = context.getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                // 不同 API 级别 getRoleHolders 签名有差异，反射兼容读取
                roleHolder = RoleHolders.primaryHolder(roleManager,
                    android.os.Process.myUserHandle());
            }
        } catch (Throwable ignored) {
            // ROM 限制时退化到 VIS / 助手组件读取
        }
        String vis = readSecure(context, "voice_interaction_service");
        String assistComponent = readSecure(context, "assistant");

        String primaryPkg = firstNonEmpty(roleHolder, packageOf(vis), packageOf(assistComponent));
        String source;
        if (roleHolder != null && !roleHolder.isEmpty()) {
            source = SystemAssistantState.SOURCE_ROLE;
        } else if (vis != null && !vis.isEmpty()) {
            source = SystemAssistantState.SOURCE_VIS;
        } else if (assistComponent != null && !assistComponent.isEmpty()) {
            source = SystemAssistantState.SOURCE_ASSIST_COMPONENT;
        } else {
            source = SystemAssistantState.SOURCE_NONE;
        }

        String label = primaryPkg == null || primaryPkg.isEmpty()
            ? "" : loadLabel(context, primaryPkg);
        boolean available = primaryPkg != null && !primaryPkg.isEmpty();
        return new SystemAssistantState(roleHolder, vis, assistComponent, label,
            available, source);
    }

    private String readSecure(Context context, String key) {
        try {
            String value = Settings.Secure.getString(context.getContentResolver(), key);
            return value == null ? "" : value.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private String loadLabel(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return String.valueOf(pm.getApplicationLabel(info));
        } catch (Throwable t) {
            return "";
        }
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    private String packageOf(String flattened) {
        if (flattened == null || flattened.isEmpty()) {
            return "";
        }
        ComponentName cn = ComponentName.unflattenFromString(flattened);
        if (cn != null) {
            return cn.getPackageName();
        }
        return flattened.contains("/")
            ? flattened.substring(0, flattened.indexOf('/'))
            : flattened;
    }
}
