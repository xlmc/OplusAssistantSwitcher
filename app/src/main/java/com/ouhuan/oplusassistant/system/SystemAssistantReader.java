package com.ouhuan.oplusassistant.system;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import com.ouhuan.oplusassistant.shared.SystemAssistantState;

/**
 * 首页「当前系统默认助手」读取（开发书 5.1）：
 * 优先 ROLE_ASSISTANT 持有者，交叉验证 VoiceInteractionService。
 * 显示必须来自系统实时状态。
 */
public final class SystemAssistantReader {

    public SystemAssistantState read(Context context) {
        String roleHolder = null;
        try {
            RoleManager roleManager = context.getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                java.util.List<String> holders =
                    roleManager.getRoleHolders(RoleManager.ROLE_ASSISTANT);
                if (holders != null && !holders.isEmpty()) {
                    roleHolder = holders.get(0);
                }
            }
        } catch (Throwable ignored) {
            // ROM 限制时退化到 VIS 读取
        }
        String vis = null;
        try {
            vis = Settings.Secure.getString(context.getContentResolver(),
                "voice_interaction_service");
        } catch (Throwable ignored) {
        }
        String primary = roleHolder != null && !roleHolder.isEmpty()
            ? roleHolder
            : packageOf(vis);
        String label = primary == null || primary.isEmpty()
            ? "" : loadLabel(context, primary);
        boolean available = (roleHolder != null && !roleHolder.isEmpty())
            || (vis != null && !vis.isEmpty());
        return new SystemAssistantState(roleHolder, vis, label, available);
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

    private String packageOf(String voiceInteractionService) {
        if (voiceInteractionService == null || voiceInteractionService.isEmpty()) {
            return "";
        }
        ComponentName cn = ComponentName.unflattenFromString(voiceInteractionService);
        if (cn != null) {
            return cn.getPackageName();
        }
        return voiceInteractionService.contains("/")
            ? voiceInteractionService.substring(0, voiceInteractionService.indexOf('/'))
            : voiceInteractionService;
    }
}
