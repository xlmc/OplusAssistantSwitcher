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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 助手选择页候选扫描（开发书 5.2 / 9；Issue #1 收紧后的规则）。
 *
 * 入选条件（全部满足）：
 * 1. 包内声明 android.service.voice.VoiceInteractionService，且该服务要求
 *    android.permission.BIND_VOICE_INTERACTION（强信号，必要条件）；
 * 2. 当前用户下已安装、应用启用、组件启用；
 * 3. 存在可实际调用的 Assistant 入口（ACTION_ASSIST 或 ACTION_VOICE_COMMAND Activity）；
 * 4. 排除欧唤自身、ColorOS 内置小布等系统原始目标、当前系统默认助手。
 *
 * ACTION_ASSIST 单独不再构成入选条件：Firefox 等仅注册 assist 响应的
 * 普通应用不会进入列表；也不使用任何固定包名白名单。
 */
public final class AssistantScanner {

    public List<AssistantCandidate> scan(Context context) {
        PackageManager pm = context.getPackageManager();

        // 1) 强信号候选：VIS 服务 + BIND_VOICE_INTERACTION 权限
        Set<String> visPackages = new LinkedHashSet<>();
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
                    visPackages.add(service.packageName);
                }
            }
        } catch (Throwable ignored) {
        }

        // 2) 过滤并解析可调用入口
        List<AssistantCandidate> result = new ArrayList<>();
        for (String pkg : visPackages) {
            if (isExcluded(context, pkg)) {
                continue;
            }
            if (!isAppEnabled(pm, pkg)) {
                continue;
            }
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
            result.add(new AssistantCandidate(pkg, loadLabel(pm, pkg), entry, method,
                true, "VoiceInteractionService + BIND_VOICE_INTERACTION"));
        }
        Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }

    private boolean isExcluded(Context context, String pkg) {
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
        SystemAssistantState systemDefault = new SystemAssistantReader().read(context);
        if (pkg.equals(systemDefault.roleHolderPackage)) {
            return true;
        }
        if (systemDefault.voiceInteractionService != null
            && systemDefault.voiceInteractionService.contains("/")
            && packagePart(systemDefault.voiceInteractionService).equals(pkg)) {
            return true;
        }
        if (systemDefault.assistComponent != null
            && systemDefault.assistComponent.contains("/")
            && packagePart(systemDefault.assistComponent).equals(pkg)) {
            return true;
        }
        return false;
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
        int slash = flattened.indexOf('/');
        return slash > 0 ? flattened.substring(0, slash) : flattened;
    }
}
