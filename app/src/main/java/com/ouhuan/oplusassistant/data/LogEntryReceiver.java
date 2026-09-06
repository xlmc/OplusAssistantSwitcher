package com.ouhuan.oplusassistant.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

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

    private static final String TAG = "OplusAssistantReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if (!isTrustedSender(context, intent)) {
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
                    // Binder 是主通道；广播仅为降级通道，但降级时也要更新同一份
                    // 运行态快照，避免诊断页只看到 Room 日志而看不到 Hook 阶段。
                    RuntimeStatusStore.apply(context, extras);
                    AssistantStateStore.apply(context, data, candidates);
                } catch (Throwable t) {
                    RuntimeDebugStore.append(context, "broadcast",
                        Constants.EV_STATE_CHANNEL_FAILED, "state_broadcast_persist",
                        "STATE_REPORT persistence failed", t);
                    Log.w(TAG, "STATE_REPORT persistence failed", t);
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
                    RuntimeStatusStore.applyEvent(context, extras);
                    LogDb.get(context).dao().insert(entity);
                    RuntimeDebugStore.append(context, "broadcast", entity.event,
                        entity.hookStatus, entity.exceptionSummary, entity.exceptionType);
                    if (Constants.KIND_HOOK.equals(entity.kind)) {
                    HookStateStore.update(context, entity);
                }
            } catch (Throwable t) {
                RuntimeDebugStore.append(context, "broadcast",
                    Constants.EV_STATE_CHANNEL_FAILED, "log_broadcast_persist",
                    "LOG_EVENT persistence failed", t);
                Log.w(TAG, "LOG_EVENT persistence failed", t);
            } finally {
                result.finish();
            }
        });
    }

    /**
     * API 34+ 直接校验真实发送方；更低版本依赖接收器上的 signature 级权限
     * （未持有权限的第三方发送者在 AMS 层即被拒绝）。
     */
    private boolean isTrustedSender(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                int sentFromUid = getSentFromUid();
                String sentFromPackage = getSentFromPackage();
                Log.i(TAG, "receive action=" + intent.getAction()
                    + " senderUid=" + sentFromUid
                    + " senderPackage=" + (sentFromPackage == null ? "" : sentFromPackage));
                boolean trusted = sentFromUid == Process.SYSTEM_UID
                    || sentFromUid == Process.myUid()
                    || Constants.MODULE_PACKAGE.equals(sentFromPackage);
                if (!trusted) {
                    String summary = "rejected senderUid=" + sentFromUid
                        + ",senderPackage=" + (sentFromPackage == null ? "" : sentFromPackage);
                    RuntimeDebugStore.append(context, "broadcast",
                        Constants.EV_STATE_CHANNEL_FAILED, "sender_identity", summary);
                    Log.w(TAG, summary);
                }
                return trusted;
            } catch (Throwable t) {
                RuntimeDebugStore.append(context, "broadcast",
                    Constants.EV_STATE_CHANNEL_FAILED, "sender_identity",
                    "cannot read broadcast sender identity", t);
                Log.w(TAG, "cannot read broadcast sender identity", t);
                return false;
            }
        }
        Log.i(TAG, "receive action=" + intent.getAction()
            + " senderIdentity=unavailable(api<34)");
        RuntimeDebugStore.append(context, "broadcast", "BROADCAST_SENDER_IDENTITY_UNAVAILABLE",
            "sender_identity", "api<34 action=" + intent.getAction());
        return true;
    }
}
