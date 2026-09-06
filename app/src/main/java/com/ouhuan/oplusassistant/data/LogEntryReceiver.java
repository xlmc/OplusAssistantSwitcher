package com.ouhuan.oplusassistant.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.ouhuan.oplusassistant.shared.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Hook 侧日志广播接收器（开发书 8.4：异步上报 → App 侧持久化）。
 * exported=false，仅 system(uid 1000) 与模块自身可送达。
 */
public class LogEntryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Constants.ACTION_LOG_EVENT.equals(intent.getAction())) {
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
