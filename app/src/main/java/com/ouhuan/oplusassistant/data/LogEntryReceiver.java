package com.ouhuan.oplusassistant.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.ouhuan.oplusassistant.shared.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Hook 侧广播接收器（开发书 8.4；Issue #1 评论 4 状态回传）。
 * LOG_EVENT：日志事件 → Room 持久化；
 * STATE_REPORT：system_server 解析的当前实际助手与候选列表 → AssistantStateStore。
 * exported=false，仅 system(uid 1000) 与模块自身可送达。
 */
public class LogEntryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
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
}
