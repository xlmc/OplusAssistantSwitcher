package com.ouhuan.oplusassistant.xposed;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.provider.Settings;

import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.ErrorCodes;
import com.ouhuan.oplusassistant.shared.LaunchResult;
import com.ouhuan.oplusassistant.shared.LaunchTarget;
import com.ouhuan.oplusassistant.shared.RoleHolders;
import com.ouhuan.oplusassistant.shared.SystemAssistantState;

import java.util.Collections;
import java.util.List;

/**
 * 读取系统默认助手、验证用户所选目标是否仍可用（开发书 4.1 / 5）。
 * 仅做包级/组件级轻量查询，禁止全量 PackageManager 扫描（开发书 6.3）。
 */
public final class AssistantResolver {

    /** 解析结果：ok 时携带可启动目标，否则携带结构化失败。 */
    public static final class ResolveOutcome {
        public final LaunchResult result;
        public final String failureCode;
        public final String summary;
        public final LaunchTarget target;

        private ResolveOutcome(LaunchResult result, String failureCode, String summary, LaunchTarget target) {
            this.result = result;
            this.failureCode = failureCode;
            this.summary = summary;
            this.target = target;
        }

        public boolean ok() {
            return target != null;
        }

        static ResolveOutcome success(LaunchTarget target) {
            return new ResolveOutcome(LaunchResult.SUCCESS, "", "", target);
        }

        static ResolveOutcome failure(LaunchResult result, String failureCode, String summary) {
            return new ResolveOutcome(result, failureCode, summary, null);
        }
    }

    /**
     * 读取当前系统默认助手（开发书 5.1）：
     * 优先 ROLE_ASSISTANT 持有者，交叉验证 VoiceInteractionService 组件。
     */
    public SystemAssistantState readSystemDefault(Context context) {
        String roleHolder = null;
        try {
            RoleManager roleManager = context.getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                // 不同 API 级别 getRoleHolders 签名有差异，反射兼容读取
                roleHolder = RoleHolders.primaryHolder(roleManager,
                    android.os.Process.myUserHandle());
            }
        } catch (Throwable ignored) {
            // 部分 ROM 限制该查询；退化到 VIS 交叉验证
        }
        String vis = null;
        try {
            vis = Settings.Secure.getString(context.getContentResolver(),
                "voice_interaction_service");
        } catch (Throwable ignored) {
        }
        String label = "";
        String primaryPkg = roleHolder != null && !roleHolder.isEmpty() ? roleHolder : vis;
        if (primaryPkg != null && !primaryPkg.isEmpty()) {
            label = loadLabel(context, primaryPkg);
        }
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

    /**
     * 验证用户选择并解析可启动入口（开发书 5.2 筛选顺序、7 启动规范）。
     */
    public ResolveOutcome resolve(Context context, RuntimeConfig.Snapshot snapshot,
                                  SystemAssistantState systemDefault) {
        PackageManager pm = context.getPackageManager();
        String pkg = snapshot.selectedPackage == null ? "" : snapshot.selectedPackage.trim();
        if (pkg.isEmpty()) {
            return ResolveOutcome.failure(LaunchResult.TARGET_NOT_FOUND,
                ErrorCodes.NO_SELECTED_ASSISTANT, null);
        }

        ApplicationInfo appInfo;
        try {
            appInfo = pm.getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return ResolveOutcome.failure(LaunchResult.TARGET_NOT_FOUND,
                ErrorCodes.ASSISTANT_NOT_INSTALLED, null);
        }
        if (!appInfo.enabled) {
            return ResolveOutcome.failure(LaunchResult.TARGET_DISABLED,
                ErrorCodes.ASSISTANT_SERVICE_DISABLED, null);
        }

        // 选择目标已成为系统默认助手：角色状态与预期不一致，不接管（错误码 12）。
        if (isSamePackage(systemDefault == null ? null : systemDefault.roleHolderPackage, pkg)) {
            return ResolveOutcome.failure(LaunchResult.ROLE_MISMATCH,
                ErrorCodes.ROLE_MISMATCH, "selected assistant became role holder");
        }
        if (isSamePackage(systemDefault == null ? null
            : packageOf(systemDefault.voiceInteractionService), pkg)) {
            return ResolveOutcome.failure(LaunchResult.ROLE_MISMATCH,
                ErrorCodes.ROLE_MISMATCH, "selected assistant became active VIS");
        }

        String storedComponent = snapshot.selectedComponent;
        String scanEntry = null;
        String scanMethod = null;

        List<ResolveInfo> assistActivities = queryActivities(pm, Intent.ACTION_ASSIST, pkg);
        if (!assistActivities.isEmpty()) {
            scanEntry = componentOf(assistActivities.get(0));
            scanMethod = Constants.LAUNCH_METHOD_ASSIST;
        }
        if (scanEntry == null) {
            List<ResolveInfo> voiceCommands = queryActivities(pm,
                Intent.ACTION_VOICE_COMMAND, pkg);
            if (!voiceCommands.isEmpty()) {
                scanEntry = componentOf(voiceCommands.get(0));
                scanMethod = Constants.LAUNCH_METHOD_VOICE_COMMAND;
            }
        }

        boolean hasVis = hasEligibleVoiceInteractionService(pm, pkg);
        if (scanEntry == null && !hasVis) {
            return ResolveOutcome.failure(LaunchResult.RESOLVE_FAILED,
                ErrorCodes.ASSISTANT_NOT_ELIGIBLE, null);
        }

        String entryComponent;
        String entryMethod;
        if (scanEntry != null) {
            entryComponent = scanEntry;
            entryMethod = scanMethod;
        } else if (storedComponent != null && !storedComponent.isEmpty()
            && isActivityResolvable(pm, storedComponent)) {
            // VIS 服务仍在而 Activity 入口发生漂移时，退回用户保存的组件
            entryComponent = storedComponent;
            entryMethod = Constants.LAUNCH_METHOD_ASSIST;
        } else if (storedComponent != null && !storedComponent.isEmpty()) {
            return ResolveOutcome.failure(LaunchResult.RESOLVE_FAILED,
                ErrorCodes.ASSISTANT_COMPONENT_MISSING, null);
        } else {
            return ResolveOutcome.failure(LaunchResult.RESOLVE_FAILED,
                ErrorCodes.RESOLVE_FAILED, "no launchable assistant entry");
        }

        return ResolveOutcome.success(new LaunchTarget(entryComponent, entryMethod));
    }

    private List<ResolveInfo> queryActivities(PackageManager pm, String action, String pkg) {
        try {
            Intent intent = new Intent(action);
            intent.setPackage(pkg);
            return pm.queryIntentActivities(intent, 0);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private boolean hasEligibleVoiceInteractionService(PackageManager pm, String pkg) {
        try {
            Intent intent = new Intent(Constants.VIS_SERVICE_INTERFACE);
            intent.setPackage(pkg);
            List<ResolveInfo> services = pm.queryIntentServices(intent,
                PackageManager.GET_META_DATA);
            for (ResolveInfo info : services) {
                ServiceInfo service = info.serviceInfo;
                if (service == null) {
                    continue;
                }
                if (Constants.PERM_BIND_VOICE_INTERACTION.equals(service.permission)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isActivityResolvable(PackageManager pm, String flattened) {
        try {
            ComponentName cn = ComponentName.unflattenFromString(flattened);
            if (cn == null) {
                return false;
            }
            pm.getActivityInfo(cn, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private String componentOf(ResolveInfo info) {
        if (info == null || info.activityInfo == null) {
            return null;
        }
        return new ComponentName(info.activityInfo.packageName, info.activityInfo.name)
            .flattenToString();
    }

    private boolean isSamePackage(String a, String b) {
        return a != null && !a.isEmpty() && a.equals(b);
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
