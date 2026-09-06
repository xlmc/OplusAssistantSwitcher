package com.ouhuan.oplusassistant.system;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import com.ouhuan.oplusassistant.shared.AssistantCandidate;
import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.SystemAssistantState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 助手候选扫描（开发书 5.2 / 9；Issue #1 P0-2 多来源候选 + 严格过滤）。
 *
 * 候选来源取并集（避免 false negative）：
 * 1. 声明 VoiceInteractionService 且要求 BIND_VOICE_INTERACTION 的应用；
 * 2. 当前 ROLE_ASSISTANT 持有者；
 * 3. 系统/ColorOS 已配置为助手的组件（voice_interaction_service / assistant）；
 * 4. 上述应用的 ACTION_ASSIST / ACTION_VOICE_COMMAND 入口经解析验证后
 *    才可入选（ACTION_ASSIST_VERIFIED）。
 *
 * 再过滤普通应用（避免 false positive）：仅响应 ACTION_ASSIST 而无任何
 * 系统级助手信号的普通应用（如 Firefox）不进入列表。
 * 排除：欧唤自身、ColorOS 内置小布等固定内置名单、厂商系统预装的
 * 当前电源键原始目标。不使用任何固定第三方包名白名单。
 */
public final class AssistantScanner {

    public static final String TAG_VIS = "VOICE_INTERACTION_SERVICE";
    public static final String TAG_ROLE = "ROLE_ASSISTANT";
    public static final String TAG_COLOROS_COMPONENT = "COLOROS_ASSISTANT_COMPONENT";
    public static final String TAG_ENTRY_VERIFIED = "ACTION_ASSIST_VERIFIED";

    public List<AssistantCandidate> scan(Context context) {
        PackageManager pm = context.getPackageManager();

        // ---- 1) 多来源候选取并集：pkg -> 资格标签 ----
        Map<String, Set<String>> sources = new LinkedHashMap<>();

        // 1a. 声明 VIS 且要求 BIND_VOICE_INTERACTION（强信号）
        try {
            List<ResolveInfo> services = pm.queryIntentServices(
                new Intent(Constants.VIS_SERVICE_INTERFACE),
                PackageManager.GET_META_DATA);
            for (ResolveInfo info : services) {
                ServiceInfo service = info == null ? null : info.serviceInfo;
                if (service == null || service.packageName == null) {
                    continue;
                }
                if (Constants.PERM_BIND_VOICE_INTERACTION.equals(service.permission)) {
                    addTag(sources, service.packageName, TAG_VIS);
                }
            }
        } catch (Throwable ignored) {
        }

        // 1b/1c. ROLE_ASSISTANT 持有者与系统已配置助手组件
        SystemAssistantState system = new SystemAssistantReader().read(context);
        if (system.roleHolderPackage != null && !system.roleHolderPackage.isEmpty()) {
            addTag(sources, system.roleHolderPackage, TAG_ROLE);
        }
        if (system.voiceInteractionService != null && !system.voiceInteractionService.isEmpty()) {
            addTag(sources, packagePart(system.voiceInteractionService), TAG_VIS);
        }
        if (system.assistComponent != null && !system.assistComponent.isEmpty()) {
            addTag(sources, packagePart(system.assistComponent), TAG_COLOROS_COMPONENT);
        }

        // ---- 2) 排除 ----
        List<AssistantCandidate> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> sourceEntry : sources.entrySet()) {
            String pkg = sourceEntry.getKey();
            if (isExcluded(context, pm, pkg, system)) {
                continue;
            }

            // ---- 3) 已安装 / 启用 ----
            if (!isAppEnabled(pm, pkg)) {
                continue;
            }

            // ---- 4) 入口解析验证（ACTION_ASSIST_VERIFIED） ----
            String entry = findEntry(pm, Intent.ACTION_ASSIST, pkg);
            String method = Constants.LAUNCH_METHOD_ASSIST;
            if (entry == null) {
                entry = findEntry(pm, Intent.ACTION_VOICE_COMMAND, pkg);
                method = Constants.LAUNCH_METHOD_VOICE_COMMAND;
            }
            if (entry == null) {
                // 无可实际调用的 Assistant 入口：不展示
                continue;
            }

            Set<String> tags = sourceEntry.getValue();
            StringBuilder eligibility = new StringBuilder();
            for (String tag : tags) {
                if (eligibility.length() > 0) {
                    eligibility.append(" + ");
                }
                eligibility.append(tag);
            }
            eligibility.append(" + ").append(TAG_ENTRY_VERIFIED);
            result.add(new AssistantCandidate(pkg, loadLabel(pm, pkg), entry, method,
                tags.contains(TAG_VIS), eligibility.toString()));
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

    /**
     * 排除规则：欧唤自身；内置小布名单；厂商系统预装（FLAG_SYSTEM 且非
     * 已更新系统应用）且正担任当前助手的包 = 系统原始目标。
     * 第三方应用即使当前是系统默认助手，也保留为候选。
     */
    private boolean isExcluded(Context context, PackageManager pm, String pkg,
                               SystemAssistantState system) {
        if (pkg == null || pkg.isEmpty()) {
            return true;
        }
        if (Constants.MODULE_PACKAGE.equals(pkg)) {
            return true;
        }
        for (String builtIn : Constants.BUILT_IN_ASSISTANT_PACKAGES) {
            if (builtIn.equals(pkg)) {
                return true;
            }
        }
        if (isVendorOriginalTarget(pm, pkg, system.roleHolderPackage)
            || isVendorOriginalTarget(pm, pkg, packagePart(system.voiceInteractionService))
            || isVendorOriginalTarget(pm, pkg, packagePart(system.assistComponent))) {
            return true;
        }
        return false;
    }

    /** 系统预装且担任当前助手 → 厂商原始目标；第三方应用不受影响。 */
    private boolean isVendorOriginalTarget(PackageManager pm, String pkg, String currentPkg) {
        if (currentPkg == null || currentPkg.isEmpty() || !currentPkg.equals(pkg)) {
            return false;
        }
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isAppEnabled(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return info.enabled;
        } catch (Throwable t) {
            return false;
        }
    }

    private String findEntry(PackageManager pm, String action, String pkg) {
        try {
            Intent intent = new Intent(action);
            intent.setPackage(pkg);
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            if (!activities.isEmpty() && activities.get(0).activityInfo != null) {
                return new android.content.ComponentName(
                    activities.get(0).activityInfo.packageName,
                    activities.get(0).activityInfo.name).flattenToString();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String loadLabel(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(info));
        } catch (Throwable t) {
            return pkg;
        }
    }

    private String packagePart(String flattened) {
        if (flattened == null || flattened.isEmpty()) {
            return "";
        }
        int slash = flattened.indexOf('/');
        return slash > 0 ? flattened.substring(0, slash) : flattened;
    }
}
