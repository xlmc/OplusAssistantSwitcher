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
 * 标准系统默认助手读取（开发书 5.1；Issue #1 P0-1）。
 *
 * 「当前系统默认助手」只反映标准 Android 状态：ROLE_ASSISTANT 持有者 →
 * VoiceInteractionService 配置。ColorOS 电源键助手组件是另一套厂商配置，
 * 单独读取（{@link #readPowerKeyComponent}），两者不得互相冒充。
 * 与 Hook 状态完全解耦：无论模块是否生效都独立读取。
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
            // ROM 限制时退化到 VIS 读取
        }
        String vis = readSecure(context, "voice_interaction_service");
        String powerKeyComponent = readSecure(context, "assistant");

        String primaryPkg = roleHolder != null && !roleHolder.isEmpty()
            ? roleHolder
            : packageOf(vis);
        String label = primaryPkg == null || primaryPkg.isEmpty()
            ? "" : loadLabel(context, primaryPkg);
        boolean available = primaryPkg != null && !primaryPkg.isEmpty();
        String source;
        if (roleHolder != null && !roleHolder.isEmpty()) {
            source = SystemAssistantState.SOURCE_ROLE;
        } else if (available) {
            source = SystemAssistantState.SOURCE_VIS;
        } else {
            source = SystemAssistantState.SOURCE_NONE;
        }
        return new SystemAssistantState(roleHolder, vis, powerKeyComponent, label,
            available, source);
    }

    /**
     * ColorOS 0.5 秒电源键原始目标（Settings.Secure assistant 组件）。
     * 这是厂商电源键配置，与标准 Android 系统默认助手是两个概念。
     */
    public String readPowerKeyComponent(Context context) {
        return readSecure(context, "assistant");
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
