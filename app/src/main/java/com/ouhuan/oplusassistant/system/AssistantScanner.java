package com.ouhuan.oplusassistant.system;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import com.ouhuan.oplusassistant.shared.AssistantCandidate;
import com.ouhuan.oplusassistant.shared.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 助手选择页候选扫描（开发书 5.2 / 9）。
 * 不使用固定名单：候选由安装状态与 Assistant 能力动态决定，
 * 仅输出已安装、组件启用、可解析且可实际调用的第三方助手。
 */
public final class AssistantScanner {

    public List<AssistantCandidate> scan(Context context) {
        PackageManager pm = context.getPackageManager();
        Set<String> visPackages = new LinkedHashSet<>();
        Set<String> assistPackages = new LinkedHashSet<>();

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

        try {
            List<ResolveInfo> activities = pm.queryIntentActivities(
                new Intent(Intent.ACTION_ASSIST), PackageManager.GET_META_DATA);
            for (ResolveInfo info : activities) {
                if (info != null && info.activityInfo != null
                    && info.activityInfo.packageName != null) {
                    assistPackages.add(info.activityInfo.packageName);
                }
            }
        } catch (Throwable ignored) {
        }

        Set<String> candidates = new LinkedHashSet<>();
        candidates.addAll(visPackages);
        candidates.addAll(assistPackages);

        List<AssistantCandidate> result = new ArrayList<>();
        for (String pkg : candidates) {
            if (isExcluded(context, pm, pkg)) {
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
                visPackages.contains(pkg)));
        }
        Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }

    private boolean isExcluded(Context context, PackageManager pm, String pkg) {
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
        com.ouhuan.oplusassistant.shared.SystemAssistantState systemDefault =
            new SystemAssistantReader().read(context);
        if (pkg.equals(systemDefault.roleHolderPackage)) {
            return true;
        }
        if (systemDefault.voiceInteractionService != null
            && systemDefault.voiceInteractionService.contains("/")
            && packagePart(systemDefault.voiceInteractionService).equals(pkg)) {
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
