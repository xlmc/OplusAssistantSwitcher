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

import com.ouhuan.oplusassistant.shared.AssistantCandidate;
import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.CurrentAssistantState;
import com.ouhuan.oplusassistant.shared.ErrorCodes;
import com.ouhuan.oplusassistant.shared.LaunchResult;
import com.ouhuan.oplusassistant.shared.LaunchTarget;
import com.ouhuan.oplusassistant.shared.RoleHolders;
import com.ouhuan.oplusassistant.shared.SystemAssistantState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 读取系统默认助手、验证用户所选目标是否仍可用（开发书 4.1 / 5）。
 * 仅做包级/组件级轻量查询，禁止全量 PackageManager 扫描（开发书 6.3）。
 *
 * Issue #1 评论 4：本类运行于 system_server，拥有完整包可见性，
 * 是「当前手机实际助手」（CurrentOplusAssistant）与候选列表的
 * 最高可信来源；结果经状态广播回传 App。
 */
public final class AssistantResolver {

    public interface DiagnosticSink {
        void onFailure(String stage, Throwable error);
    }

    private final DiagnosticSink diagnosticSink;

    public AssistantResolver() {
        this(null);
    }

    public AssistantResolver(DiagnosticSink diagnosticSink) {
        this.diagnosticSink = diagnosticSink;
    }

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

    /** 入口解析结果：组件 + 组件自身 label（用于 UI 显示产品名而非包名）。 */
    private static final class EntryResult {
        final String component;
        final String label;
        final String method;

        EntryResult(String component, String label, String method) {
            this.component = component;
            this.label = label;
            this.method = method;
        }
    }

    // ------------------------------------------------------------------
    // 当前手机实际助手（CurrentOplusAssistant）
    // ------------------------------------------------------------------

    /**
     * 以 system_server 上下文解析「当前手机实际助手」。
     * 可信来源优先级（Issue #1 评论 4）：
     * 1. 电源键原始目标组件（Settings.Secure assistant，厂商电源键配置）；
     * 2. OEM 语音服务（com.coloros / com.heytap / com.oplus / com.oppo /
     *    com.oneplus 前缀包内声明 VoiceInteractionService，即小布等内置助手，
     *    不写死具体包名，实际组件来自设备真实检测）；
     * 3. 标准 ROLE_ASSISTANT 持有者。
     * 显示名取组件自身 label（其次应用名），保证「Gemini 显示为 Gemini、
     * 旧 Google Assistant 显示为 Google」由组件真实 label 决定。
     */
    public CurrentAssistantState readCurrentOplusAssistant(Context context) {
        PackageManager pm = context.getPackageManager();
        SystemAssistantState standard = readSystemDefault(context);

        String name = "";
        String pkg = "";
        String component = "";
        String source = CurrentAssistantState.SOURCE_NONE;

        // 1) 电源键原始目标（最高可信）
        String assistComponent = standard.assistComponent;
        if (!assistComponent.isEmpty() && isKnownComponent(pm, assistComponent)) {
            // Settings.Secure 中的原始组件是最高可信来源。即使该组件没有
            // label，也不能因为 UI 元数据缺失而退到任意一个 OEM 服务。
            String rawPackage = packageOf(assistComponent);
            String label = loadComponentLabel(pm, assistComponent);
            if (label.isEmpty()) {
                label = appLabel(pm, rawPackage);
            }
            name = label.isEmpty() ? "系统助手" : label;
            pkg = rawPackage;
            component = assistComponent;
            source = CurrentAssistantState.SOURCE_POWER_KEY;
        }

        // 2) OEM 内置语音服务（小布等）
        if (CurrentAssistantState.SOURCE_NONE.equals(source)) {
            ResolveInfo service = findOemVoiceService(pm, standard.voiceInteractionService);
            if (service != null && service.serviceInfo != null) {
                ServiceInfo info = service.serviceInfo;
                CharSequence label = info.loadLabel(pm);
                if (label == null || label.length() == 0) {
                    label = appLabel(pm, info.packageName);
                }
                name = label == null || label.length() == 0
                    ? "系统助手" : String.valueOf(label);
                pkg = info.packageName;
                component = new ComponentName(info.packageName, info.name).flattenToString();
                source = CurrentAssistantState.SOURCE_OEM_VOICE_SERVICE;
            }
        }

        // 3) 标准 ROLE_ASSISTANT 持有者
        if (CurrentAssistantState.SOURCE_NONE.equals(source) && standard.isAvailable) {
            name = standard.label == null || standard.label.isEmpty()
                ? "系统助手" : standard.label;
            pkg = standard.roleHolderPackage != null && !standard.roleHolderPackage.isEmpty()
                ? standard.roleHolderPackage
                : packageOf(standard.voiceInteractionService);
            component = standard.voiceInteractionService == null ? "" : standard.voiceInteractionService;
            source = CurrentAssistantState.SOURCE_ROLE_HOLDER;
        }

        return new CurrentAssistantState(name, pkg, component, source, true,
            standard.roleHolderPackage, standard.voiceInteractionService,
            System.currentTimeMillis());
    }

    /** 在完整包可见性下查找 OEM 内置语音服务（com.coloros / heytap / oplus / oppo / oneplus 前缀）。 */
    private ResolveInfo findOemVoiceService(PackageManager pm, String configuredVoiceService) {
        try {
            // 如果系统已经给出了具体 VIS 组件，优先验证它；不能在多个 OEM
            // 语音服务之间按 query 顺序任选一个冒充当前助手。
            ResolveInfo configured = resolveOemVoiceService(pm, configuredVoiceService);
            if (configured != null) {
                return configured;
            }
            List<ResolveInfo> services = pm.queryIntentServices(
                new Intent(Constants.VIS_SERVICE_INTERFACE), PackageManager.GET_META_DATA);
            for (ResolveInfo info : services) {
                ServiceInfo service = info == null ? null : info.serviceInfo;
                if (service == null || service.packageName == null) {
                    continue;
                }
                if (!Constants.PERM_BIND_VOICE_INTERACTION.equals(service.permission)) {
                    continue;
                }
                if (!isOemPackage(service.packageName)) {
                    continue;
                }
                if (isAppEnabled(pm, service.packageName)) {
                    return info;
                }
            }
            // 禁用的 OEM VoiceInteractionService 不是当前实际助手，不能把它
            // 显示成系统默认助手；标准 ROLE/VIS 读取会在下方继续兜底。
            return null;
        } catch (Throwable t) {
            reportFailure("current_oem_voice_service_query", t);
            return null;
        }
    }

    /** 从当前系统设置验证具体 OEM VoiceInteractionService。 */
    private ResolveInfo resolveOemVoiceService(PackageManager pm, String flattened) {
        if (flattened == null || flattened.isEmpty()) {
            return null;
        }
        ComponentName component = ComponentName.unflattenFromString(flattened);
        if (component == null || !isOemPackage(component.getPackageName())) {
            return null;
        }
        try {
            ServiceInfo service = pm.getServiceInfo(component, 0);
            if (!Constants.PERM_BIND_VOICE_INTERACTION.equals(service.permission)
                || !isAppEnabled(pm, service.packageName)) {
                return null;
            }
            ResolveInfo result = new ResolveInfo();
            result.serviceInfo = service;
            return result;
        } catch (Throwable t) {
            reportFailure("configured_oem_voice_service_query", t);
            return null;
        }
    }

    private boolean isOemPackage(String pkg) {
        if (pkg == null) {
            return false;
        }
        for (String prefix : Constants.OEM_ASSISTANT_PACKAGE_PREFIXES) {
            if (pkg.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 候选扫描（system_server 侧，完整包可见性）
    // ------------------------------------------------------------------

    /**
     * system_server 侧候选扫描（与 App 侧 AssistantScanner 同一套规则）：
     * 多来源并集（VIS+权限 / ROLE_ASSISTANT / 已配置助手组件）→ 排除
     * （欧唤自身、内置小布名单、厂商预装的当前原始目标）→ 启用 →
     * 入口可实际调用验证。仅做 intent 过滤查询，非全量扫描。
     */
    public List<AssistantCandidate> scanCandidates(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, Set<String>> sources = new LinkedHashMap<>();

        try {
            List<ResolveInfo> services = pm.queryIntentServices(
                new Intent(Constants.VIS_SERVICE_INTERFACE), PackageManager.GET_META_DATA);
            for (ResolveInfo info : services) {
                ServiceInfo service = info == null ? null : info.serviceInfo;
                if (service == null || service.packageName == null) {
                    continue;
                }
                if (Constants.PERM_BIND_VOICE_INTERACTION.equals(service.permission)) {
                    addTag(sources, service.packageName, "VOICE_INTERACTION_SERVICE");
                }
            }
        } catch (Throwable ignored) {
            reportFailure("candidate_vis_query", ignored);
        }

        SystemAssistantState system = readSystemDefault(context);
        if (system.roleHolderPackage != null && !system.roleHolderPackage.isEmpty()) {
            addTag(sources, system.roleHolderPackage, "ROLE_ASSISTANT");
        }
        if (system.voiceInteractionService != null && !system.voiceInteractionService.isEmpty()) {
            addTag(sources, packageOf(system.voiceInteractionService), "VOICE_INTERACTION_SERVICE");
        }
        if (system.assistComponent != null && !system.assistComponent.isEmpty()) {
            addTag(sources, packageOf(system.assistComponent), "COLOROS_ASSISTANT_COMPONENT");
        }

        List<AssistantCandidate> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> sourceEntry : sources.entrySet()) {
            String candidatePkg = sourceEntry.getKey();
            if (isExcluded(pm, candidatePkg, system) || !isAppEnabled(pm, candidatePkg)) {
                continue;
            }
            EntryResult entry = findEntry(pm, Intent.ACTION_ASSIST, candidatePkg);
            if (entry == null) {
                entry = findEntry(pm, Intent.ACTION_VOICE_COMMAND, candidatePkg);
            }
            if (entry == null) {
                continue;
            }
            StringBuilder eligibility = new StringBuilder();
            for (String tag : sourceEntry.getValue()) {
                if (eligibility.length() > 0) {
                    eligibility.append(" + ");
                }
                eligibility.append(tag);
            }
            eligibility.append(" + ACTION_ASSIST_VERIFIED");
            String label = entry.label.isEmpty() ? appLabel(pm, candidatePkg) : entry.label;
            if (label.isEmpty()) {
                label = candidatePkg;
            }
            result.add(new AssistantCandidate(candidatePkg, label, entry.component,
                entry.method, sourceEntry.getValue().contains("VOICE_INTERACTION_SERVICE"),
                eligibility.toString()));
        }
        Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }

    private static void addTag(Map<String, Set<String>> sources, String pkg, String tag) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        Set<String> tags = sources.get(pkg);
        if (tags == null) {
            tags = new LinkedHashSet<>();
            sources.put(pkg, tags);
        }
        tags.add(tag);
    }

    private boolean isExcluded(PackageManager pm, String pkg, SystemAssistantState system) {
        if (pkg == null || pkg.isEmpty() || Constants.MODULE_PACKAGE.equals(pkg)) {
            return true;
        }
        for (String builtIn : Constants.BUILT_IN_ASSISTANT_PACKAGES) {
            if (builtIn.equals(pkg)) {
                return true;
            }
        }
        if (isVendorOriginalTarget(pm, pkg, system.roleHolderPackage)
            || isVendorOriginalTarget(pm, pkg, packageOf(system.voiceInteractionService))
            || isVendorOriginalTarget(pm, pkg, packageOf(system.assistComponent))) {
            return true;
        }
        return false;
    }

    private boolean isVendorOriginalTarget(PackageManager pm, String pkg, String currentPkg) {
        if (currentPkg == null || currentPkg.isEmpty() || !currentPkg.equals(pkg)) {
            return false;
        }
        if (!isOemPackage(pkg)) {
            return false;
        }
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        } catch (Throwable t) {
            reportFailure("vendor_original_target_query", t);
            return false;
        }
    }

    private boolean isKnownComponent(PackageManager pm, String flattened) {
        ComponentName component = ComponentName.unflattenFromString(flattened);
        if (component == null) {
            return false;
        }
        try {
            pm.getActivityInfo(component, 0);
            return true;
        } catch (Throwable ignored) {
            // Settings.Secure assistant may point to a service rather than an Activity.
        }
        try {
            pm.getServiceInfo(component, 0);
            return true;
        } catch (Throwable t) {
            reportFailure("current_assistant_component_query", t);
            return false;
        }
    }

    private boolean isAppEnabled(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return info.enabled;
        } catch (Throwable t) {
            reportFailure("application_enabled_query", t);
            return false;
        }
    }

    private EntryResult findEntry(PackageManager pm, String action, String pkg) {
        try {
            Intent intent = new Intent(action);
            intent.setPackage(pkg);
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            if (!activities.isEmpty() && activities.get(0).activityInfo != null) {
                ResolveInfo info = activities.get(0);
                String component = new ComponentName(info.activityInfo.packageName,
                    info.activityInfo.name).flattenToString();
                CharSequence label = info.loadLabel(pm);
                String method = Intent.ACTION_ASSIST.equals(action)
                    ? Constants.LAUNCH_METHOD_ASSIST
                    : Constants.LAUNCH_METHOD_VOICE_COMMAND;
                return new EntryResult(component,
                    label == null ? "" : String.valueOf(label), method);
            }
        } catch (Throwable ignored) {
            reportFailure("assistant_entry_query", ignored);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 标准系统默认助手（AndroidAssistantRoleState，仅供诊断）
    // ------------------------------------------------------------------

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
            reportFailure("role_holder_query", ignored);
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

    // ------------------------------------------------------------------
    // 选择校验
    // ------------------------------------------------------------------

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
        } catch (Throwable t) {
            reportFailure("resolve_application_query", t);
            return ResolveOutcome.failure(LaunchResult.RESOLVE_FAILED,
                ErrorCodes.RESOLVE_FAILED, t.getClass().getSimpleName() + ": " + t.getMessage());
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

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private List<ResolveInfo> queryActivities(PackageManager pm, String action, String pkg) {
        try {
            Intent intent = new Intent(action);
            intent.setPackage(pkg);
            return pm.queryIntentActivities(intent, 0);
        } catch (Throwable t) {
            reportFailure("launchable_activity_query", t);
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
            reportFailure("voice_interaction_service_query", ignored);
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
            reportFailure("activity_resolvable_query", t);
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

    private String readSecure(Context context, String key) {
        try {
            String value = Settings.Secure.getString(context.getContentResolver(), key);
            return value == null ? "" : value.trim();
        } catch (Throwable t) {
            reportFailure("secure_setting_query", t);
            return "";
        }
    }

    /** 组件自身 label（Activity/Service），取不到回退应用名，再回退空串。 */
    private String loadComponentLabel(PackageManager pm, String flattened) {
        ComponentName cn = ComponentName.unflattenFromString(flattened);
        if (cn == null) {
            return "";
        }
        try {
            CharSequence label = pm.getActivityInfo(cn, 0).loadLabel(pm);
            if (label != null && label.length() > 0) {
                return String.valueOf(label);
            }
        } catch (Throwable ignored) {
            reportFailure("activity_label_query", ignored);
        }
        try {
            CharSequence label = pm.getServiceInfo(cn, 0).loadLabel(pm);
            if (label != null && label.length() > 0) {
                return String.valueOf(label);
            }
        } catch (Throwable ignored) {
            reportFailure("service_label_query", ignored);
        }
        return appLabel(pm, cn.getPackageName());
    }

    private String appLabel(PackageManager pm, String packageName) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? "" : String.valueOf(label);
        } catch (Throwable t) {
            reportFailure("application_label_query", t);
            return "";
        }
    }

    private String loadLabel(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        String label = appLabel(pm, packageName);
        return label.isEmpty() ? packageName : label;
    }

    private void reportFailure(String stage, Throwable error) {
        DiagnosticSink sink = diagnosticSink;
        if (sink == null) {
            return;
        }
        try {
            sink.onFailure(stage, error);
        } catch (Throwable callbackFailure) {
            android.util.Log.w(Constants.MODULE_PACKAGE,
                "resolver diagnostic callback failed: " + callbackFailure.getClass().getName()
                    + ": " + callbackFailure.getMessage(), callbackFailure);
        }
    }
}
