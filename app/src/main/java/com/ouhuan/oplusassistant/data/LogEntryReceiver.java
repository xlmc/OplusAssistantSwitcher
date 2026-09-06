package com.ouhuan.oplusassistant.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;

import com.ouhuan.oplusassistant.shared.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Hook 侧广播接收器（开发书 8.4；Issue #1 评论 4 状态回传；Issue #1 P0 回传通道修复）。
 * LOG_EVENT：日志事件 → Room 持久化；
 * STATE_REPORT：system_server 解析的当前实际助手与候选列表 → AssistantStateStore。
 *
 * 通道保护（双层）：
 * 1. Manifest：exported=true + signature 级权限 PERMISSION_REPORT，
 *    第三方应用不持有该权限、无法投递；system(uid 1000) 发送方由 AMS 组件检查短路放行；
 * 2. 代码：API 34+ 校验 getSentFromUid/getSentFromPackage 必须为 system 或模块自身。
 */
public class LogEntryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if (!isTrustedSender(intent)) {
            return;
        }
        Bundle extras = intent.getExtras();
        if (extras == null || extras.isEmpty()) {
            return;
        }
        Map<String, String> data = new HashMap<>();
        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            if (value != null) {
                data.put(key, String.valueOf(value));
            }
        }
        final PendingResult result = goAsync();
        if (Constants.ACTION_STATE_REPORT.equals(intent.getAction())) {
            final ArrayList<String> candidates =
                intent.getStringArrayListExtra(Constants.STATE_CANDIDATES);
            AppExecutors.io().execute(() -> {
                try {
                    AssistantStateStore.apply(context, data, candidates);
                } catch (Throwable ignored) {
                } finally {
                    result.finish();
                }
            });
            return;
        }
        if (!Constants.ACTION_LOG_EVENT.equals(intent.getAction())) {
            result.finish();
            return;
        }
        AppExecutors.io().execute(() -> {
            try {
                LogEntity entity = LogEntity.fromMap(data);
                LogDb.get(context).dao().insert(entity);
                if (Constants.KIND_HOOK.equals(entity.kind)) {
                    HookStateStore.update(context, entity);
                }
            } catch (Throwable ignored) {
                // 持久化失败不影响广播应答
            } finally {
                result.finish();
            }
        });
    }

    /**
     * API 34+ 直接校验真实发送方；更低版本依赖接收器上的 signature 级权限
     * （未持有权限的第三方发送者在 AMS 层即被拒绝）。
     */
    private static boolean isTrustedSender(Intent intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            int sentFromUid = intent.getSentFromUid();
            if (sentFromUid == Process.SYSTEM_UID || sentFromUid == Process.myUid()) {
                return true;
            }
            String sentFromPackage = intent.getSentFromPackage();
            return Constants.MODULE_PACKAGE.equals(sentFromPackage);
        }
        return true;
    }
}
