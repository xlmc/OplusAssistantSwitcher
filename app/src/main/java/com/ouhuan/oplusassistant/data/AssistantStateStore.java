package com.ouhuan.oplusassistant.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.ouhuan.oplusassistant.shared.AssistantCandidate;
import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.CurrentAssistantState;
import com.ouhuan.oplusassistant.shared.ModuleVersionState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * system_server 状态广播的本地持久化（Issue #1 评论 4）：
 * 保存「当前手机实际助手」（CurrentOplusAssistant）与
 * system_server 侧扫描的候选列表，供首页 / 选择页 / 诊断页读取。
 */
public final class AssistantStateStore {

    private static final String PREFS = "assistant_state";
    private static final String KEY_NAME = "current_name";
    private static final String KEY_PACKAGE = "current_package";
    private static final String KEY_COMPONENT = "current_component";
    private static final String KEY_SOURCE = "current_source";
    private static final String KEY_ROLE_HOLDER = "role_holder";
    private static final String KEY_VIS = "voice_interaction_service";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_CANDIDATES = "candidates";
    private static final String KEY_MODULE_VERSION_NAME = "module_version_name";
    private static final String KEY_MODULE_VERSION_CODE = "module_version_code";
    private static final String KEY_MODULE_LOADED_AT = "module_loaded_at";

    /** 候选条目字段分隔符。 */
    public static final String SEP_FIELD = "\u0001";
    /** 候选条目之间分隔符。 */
    public static final String SEP_ENTRY = "\u0002";

    private AssistantStateStore() {
    }

    /** 收到状态广播后落库（必须在后台线程调用）。 */
    public static void apply(Context context, Map<String, String> data,
                             List<String> candidateEntries) {
        if (context == null || data == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putString(KEY_NAME, safe(data.get(Constants.STATE_CURRENT_NAME)));
        editor.putString(KEY_PACKAGE, safe(data.get(Constants.STATE_CURRENT_PACKAGE)));
        editor.putString(KEY_COMPONENT, safe(data.get(Constants.STATE_CURRENT_COMPONENT)));
        editor.putString(KEY_SOURCE, safe(data.get(Constants.STATE_CURRENT_SOURCE)));
        editor.putString(KEY_ROLE_HOLDER, safe(data.get(Constants.STATE_ROLE_HOLDER)));
        editor.putString(KEY_VIS, safe(data.get(Constants.STATE_VOICE_INTERACTION_SERVICE)));
        long ts = 0;
        try {
            ts = Long.parseLong(safe(data.get(Constants.STATE_TIMESTAMP)));
        } catch (NumberFormatException ignored) {
        }
        editor.putLong(KEY_TIMESTAMP, ts == 0 ? System.currentTimeMillis() : ts);
        if (data.containsKey(Constants.STATE_MODULE_VERSION_NAME)) {
            editor.putString(KEY_MODULE_VERSION_NAME,
                safe(data.get(Constants.STATE_MODULE_VERSION_NAME)));
        }
        if (data.containsKey(Constants.STATE_MODULE_VERSION_CODE)) {
            editor.putLong(KEY_MODULE_VERSION_CODE,
                parseLong(data.get(Constants.STATE_MODULE_VERSION_CODE),
                    ModuleVersionState.UNKNOWN_VERSION_CODE));
        }
        if (data.containsKey(Constants.STATE_MODULE_LOADED_AT)) {
            editor.putLong(KEY_MODULE_LOADED_AT,
                parseLong(data.get(Constants.STATE_MODULE_LOADED_AT), 0L));
        }
        if (candidateEntries != null) {
            StringBuilder sb = new StringBuilder();
            for (String entry : candidateEntries) {
                if (entry == null || entry.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(SEP_ENTRY);
                }
                sb.append(entry);
            }
            editor.putString(KEY_CANDIDATES, sb.toString());
        }
        editor.apply();
    }

    /** 最近一次 system_server 上报的「当前手机实际助手」；从未上报返回 null。 */
    public static CurrentAssistantState current(Context context) {
        SharedPreferences p = prefs(context);
        long ts = p.getLong(KEY_TIMESTAMP, 0);
        if (ts == 0) {
            return null;
        }
        return new CurrentAssistantState(
            p.getString(KEY_NAME, ""),
            p.getString(KEY_PACKAGE, ""),
            p.getString(KEY_COMPONENT, ""),
            p.getString(KEY_SOURCE, CurrentAssistantState.SOURCE_NONE),
            true,
            p.getString(KEY_ROLE_HOLDER, ""),
            p.getString(KEY_VIS, ""),
            ts);
    }

    /** 最近一次 system_server 上报的实际模块版本；从未上报返回 null。 */
    public static ModuleVersionState moduleVersion(Context context) {
        SharedPreferences p = prefs(context);
        long loadedAt = p.getLong(KEY_MODULE_LOADED_AT, 0L);
        if (loadedAt <= 0L) {
            return null;
        }
        return new ModuleVersionState(
            p.getString(KEY_MODULE_VERSION_NAME, ""),
            p.getLong(KEY_MODULE_VERSION_CODE, ModuleVersionState.UNKNOWN_VERSION_CODE),
            loadedAt);
    }

    /** system_server 侧扫描到的候选列表（可能与本地扫描合并去重）。 */
    public static List<AssistantCandidate> candidates(Context context) {
        List<AssistantCandidate> result = new ArrayList<>();
        String raw = prefs(context).getString(KEY_CANDIDATES, "");
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String entry : raw.split(SEP_ENTRY, -1)) {
            String[] fields = entry.split(SEP_FIELD, -1);
            if (fields.length < 4 || fields[1].isEmpty() || fields[2].isEmpty()) {
                continue;
            }
            String eligibility = fields.length > 4 ? fields[4] : "";
            result.add(new AssistantCandidate(fields[1], fields[0], fields[2],
                fields[3], eligibility.contains("VOICE_INTERACTION_SERVICE"), eligibility));
        }
        return result;
    }

    public static long timestamp(Context context) {
        return prefs(context).getLong(KEY_TIMESTAMP, 0);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(safe(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
