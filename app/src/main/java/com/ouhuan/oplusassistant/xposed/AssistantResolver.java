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
     * 读取当前标准系统默认助手（开发书 5.1；Issue #1 P0-1）：
     * 只反映 ROLE_ASSISTANT 持有者 / VoiceInteractionService 配置；
     * ColorOS 电源键助手组件仅保留原始值（assistComponent），不冒充标准状态。
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
        String vis = readSecure(context, "voice_interaction_service");
        String assistComponent = readSecure(context, "assistant");

        String primaryPkg = roleHolder != null && !roleHolder.isEmpty()
            ? roleHolder
            : packageOf(vis);
        String label = primaryPkg != null && !primaryPkg.isEmpty()
            ? loadLabel(context, primaryPkg) : "";
        boolean available = primaryPkg != null && !primaryPkg.isEmpty();
        String source;
        if (roleHolder != null && !roleHolder.isEmpty()) {
            source = SystemAssistantState.SOURCE_ROLE;
        } else if (available) {
            source = SystemAssistantState.SOURCE_VIS;
        } else {
            source = SystemAssistantState.SOURCE_NONE;
        }
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

    /**
     * 验证用户选择并解析可启动入口（开发书 5.2 筛选顺序、7 启动规范；
     * Issue #1 P0-2 多来源资格）。
     *
     * 资格判定（任一成立即视为系统级助手，避免 false negative）：
     * 1. 包内存在要求 BIND_VOICE_INTERACTION 的 VoiceInteractionService；
     * 2. 该包是当前 ROLE_ASSISTANT 持有者；
     * 3. 该包是当前已配置助手组件（voice_interaction_service / assistant）。
     * 仅响应 ACTION_ASSIST 而无任何系统级助手信号的普通应用（如 Firefox）
     * 不具备资格，保持 ASSISTANT_NOT_ELIGIBLE 静默结束。
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

        boolean hasVis = hasEligibleVoiceInteractionService(pm, pkg);
        SystemAssistantState sys = systemDefault;
        boolean isRoleHolder = sys != null && isSamePackage(sys.roleHolderPackage, pkg);
        boolean isActiveVis = sys != null && isSamePackage(packageOf(sys.voiceInteractionService), pkg);
        boolean isConfiguredAssistant = sys != null
            && isSamePackage(packageOf(sys.assistComponent), pkg);
        if (!hasVis && !isRoleHolder && !isActiveVis && !isConfiguredAssistant) {
            return ResolveOutcome.failure(LaunchResult.RESOLVE_FAILED,
                ErrorCodes.ASSISTANT_NOT_ELIGIBLE,
                "no system-level assistant signal (VIS / role holder / configured component)");
        }

        String storedComponent = snapshot.selectedComponent;
        String entryComponent;
        String entryMethod;
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

        if (scanEntry != null) {
            entryComponent = scanEntry;
            entryMethod = scanMethod;
        } else if (storedComponent != null && !storedComponent.isEmpty()
            && isActivityResolvable(pm, storedComponent)) {
            // 入口发生漂移时，退回用户保存的组件
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
