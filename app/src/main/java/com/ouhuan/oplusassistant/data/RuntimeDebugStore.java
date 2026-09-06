package com.ouhuan.oplusassistant.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.ouhuan.oplusassistant.shared.Constants;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 运行态诊断环形缓冲区。
 *
 * <p>App 侧的注册、配置、Binder ping 与 system_server 通过 Binder 上报的生命周期事件
 * 都写入同一份最多 50 条的本地记录，诊断页可以在无需重启的情况下复制出来。</p>
 */
public final class RuntimeDebugStore {

    private static final String PREFS = "runtime_debug";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_APP_STAGE = "app_stage";
    private static final String KEY_APP_STATUS = "app_status";
    private static final String KEY_APP_WAIT_START_AT = "app_wait_start_at";
    private static final String KEY_APP_REGISTERED_AT = "app_registered_at";
    private static final String KEY_APP_BOUND_AT = "app_bound_at";
    private static final String KEY_APP_LAST_EVENT = "app_last_event";
    private static final String KEY_APP_LAST_EVENT_AT = "app_last_event_at";
    private static final String KEY_APP_ERROR = "app_error";
    private static final String ENTRY_SEPARATOR = "\u0002";
    private static final String FIELD_SEPARATOR = "\u0001";
    private static final int MAX_ENTRIES = 50;
    private static final Object LOCK = new Object();

    private RuntimeDebugStore() {
    }

    public static void append(Context context, String source, String event, String stage,
            String summary) {
        append(context, source, event, stage, summary, "");
    }

    public static void append(Context context, String source, String event, String stage,
            String summary, Throwable error) {
        String exception = error == null ? "" : exceptionText(error);
        append(context, source, event, stage, summary, exception);
    }

    public static void append(Context context, String source, String event, String stage,
            String summary, String exception) {
        if (context == null) {
            return;
        }
        try {
            String record = encodeRecord(System.currentTimeMillis(), source, event, stage,
                summary, exception);
            synchronized (LOCK) {
                SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String raw = prefs.getString(KEY_ENTRIES, "");
                List<String> records = splitRecords(raw);
                records.add(record);
                while (records.size() > MAX_ENTRIES) {
                    records.remove(0);
                }
                SharedPreferences.Editor editor = prefs.edit()
                    .putString(KEY_ENTRIES, joinRecords(records));
                updateAppLifecycle(editor, source, event, System.currentTimeMillis(), summary,
                    exception);
                editor.apply();
            }
        } catch (Throwable ignored) {
            // 诊断记录不能影响主链路；Logcat 由调用方负责兜底。
        }
    }

    /** App ↔ XposedService 状态机的直接快照，供诊断页显示“卡在哪一层”。 */
    public static AppLifecycleSnapshot appLifecycle(Context context) {
        if (context == null) {
            return new AppLifecycleSnapshot("", "UNKNOWN", 0L, 0L, 0L, "", 0L, "");
        }
        try {
            SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long waitStart = prefs.getLong(KEY_APP_WAIT_START_AT, 0L);
            long registeredAt = prefs.getLong(KEY_APP_REGISTERED_AT, 0L);
            long boundAt = prefs.getLong(KEY_APP_BOUND_AT, 0L);
            long now = System.currentTimeMillis();
            long waitEnd = boundAt > 0L ? boundAt : now;
            long waitMs = waitStart > 0L && waitEnd >= waitStart
                ? waitEnd - waitStart : 0L;
            return new AppLifecycleSnapshot(
                prefs.getString(KEY_APP_STAGE, ""),
                prefs.getString(KEY_APP_STATUS, "UNKNOWN"),
                waitStart,
                registeredAt,
                boundAt,
                prefs.getString(KEY_APP_LAST_EVENT, ""),
                prefs.getLong(KEY_APP_LAST_EVENT_AT, 0L),
                prefs.getString(KEY_APP_ERROR, ""),
                waitMs);
        } catch (Throwable ignored) {
            return new AppLifecycleSnapshot("", "UNKNOWN", 0L, 0L, 0L, "", 0L, "");
        }
    }

    public static final class AppLifecycleSnapshot {
        public final String stage;
        public final String status;
        public final long waitStartAt;
        public final long registeredAt;
        public final long boundAt;
        public final String lastEvent;
        public final long lastEventAt;
        public final String error;
        public final long waitDurationMs;

        private AppLifecycleSnapshot(String stage, String status, long waitStartAt,
                long registeredAt, long boundAt, String lastEvent, long lastEventAt,
                String error) {
            this(stage, status, waitStartAt, registeredAt, boundAt, lastEvent, lastEventAt,
                error, 0L);
        }

        private AppLifecycleSnapshot(String stage, String status, long waitStartAt,
                long registeredAt, long boundAt, String lastEvent, long lastEventAt,
                String error, long waitDurationMs) {
            this.stage = stage;
            this.status = status;
            this.waitStartAt = waitStartAt;
            this.registeredAt = registeredAt;
            this.boundAt = boundAt;
            this.lastEvent = lastEvent;
            this.lastEventAt = lastEventAt;
            this.error = error;
            this.waitDurationMs = waitDurationMs;
        }
    }

    /** 返回从新到旧的最近记录。 */
    public static List<Entry> recent(Context context, int limit) {
        if (context == null || limit <= 0) {
            return Collections.emptyList();
        }
        try {
            synchronized (LOCK) {
                SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                List<String> records = splitRecords(prefs.getString(KEY_ENTRIES, ""));
                ArrayList<Entry> result = new ArrayList<>();
                for (int i = records.size() - 1; i >= 0 && result.size() < limit; i--) {
                    Entry entry = decodeRecord(records.get(i));
                    if (entry != null) {
                        result.add(entry);
                    }
                }
                return result;
            }
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    public static String format(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "(no debug events)";
        }
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault());
        for (Entry entry : entries) {
            sb.append(format.format(new Date(entry.timestamp)))
                .append(" [").append(value(entry.source)).append("] ")
                .append(value(entry.event));
            if (!isEmpty(entry.stage)) {
                sb.append(" stage=").append(entry.stage);
            }
            if (!isEmpty(entry.summary)) {
                sb.append(" ").append(entry.summary);
            }
            if (!isEmpty(entry.exception)) {
                sb.append(" exception=").append(entry.exception);
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public static final class Entry {
        public final long timestamp;
        public final String source;
        public final String event;
        public final String stage;
        public final String summary;
        public final String exception;

        private Entry(long timestamp, String source, String event, String stage, String summary,
                String exception) {
            this.timestamp = timestamp;
            this.source = source;
            this.event = event;
            this.stage = stage;
            this.summary = summary;
            this.exception = exception;
        }
    }

    private static String encodeRecord(long timestamp, String source, String event, String stage,
            String summary, String exception) {
        return timestamp + FIELD_SEPARATOR
            + encode(source) + FIELD_SEPARATOR
            + encode(event) + FIELD_SEPARATOR
            + encode(stage) + FIELD_SEPARATOR
            + encode(summary) + FIELD_SEPARATOR
            + encode(exception);
    }

    private static Entry decodeRecord(String record) {
        if (record == null || record.isEmpty()) {
            return null;
        }
        String[] fields = record.split(FIELD_SEPARATOR, -1);
        if (fields.length != 6) {
            return null;
        }
        try {
            return new Entry(Long.parseLong(fields[0]), decode(fields[1]), decode(fields[2]),
                decode(fields[3]), decode(fields[4]), decode(fields[5]));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<String> splitRecords(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        String[] values = raw.split(ENTRY_SEPARATOR, -1);
        ArrayList<String> result = new ArrayList<>(values.length);
        Collections.addAll(result, values);
        return result;
    }

    private static String joinRecords(List<String> records) {
        StringBuilder sb = new StringBuilder();
        for (String record : records) {
            if (sb.length() > 0) {
                sb.append(ENTRY_SEPARATOR);
            }
            sb.append(record);
        }
        return sb.toString();
    }

    private static String encode(String value) {
        String safe = value == null ? "" : value;
        return Base64.encodeToString(safe.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String decode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }

    private static String exceptionText(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getName() + (isEmpty(message) ? "" : ": " + message);
    }

    private static void updateAppLifecycle(SharedPreferences.Editor editor, String source,
            String event, long now, String summary, String exception) {
        if (!"app".equals(source) || event == null) {
            return;
        }
        editor.putString(KEY_APP_LAST_EVENT, event)
            .putLong(KEY_APP_LAST_EVENT_AT, now);
        String detail = isEmpty(exception) ? value(summary) : exception;
        if (!isEmpty(exception) || event.endsWith("FAILED") || event.endsWith("DIED")) {
            editor.putString(KEY_APP_ERROR, detail);
        }
        if (Constants.EV_APP_ON_CREATE.equals(event)) {
            editor.putString(KEY_APP_STAGE, "application")
                .putString(KEY_APP_STATUS, "CREATED")
                .putLong(KEY_APP_WAIT_START_AT, 0L)
                .putLong(KEY_APP_REGISTERED_AT, 0L)
                .putLong(KEY_APP_BOUND_AT, 0L)
                .putString(KEY_APP_ERROR, "");
        } else if (Constants.EV_XPOSED_LISTENER_REGISTER_BEGIN.equals(event)) {
            editor.putString(KEY_APP_STAGE, "registerListener")
                .putString(KEY_APP_STATUS, "REGISTERING")
                .putLong(KEY_APP_WAIT_START_AT, now)
                .putLong(KEY_APP_REGISTERED_AT, 0L)
                .putLong(KEY_APP_BOUND_AT, 0L)
                .putString(KEY_APP_ERROR, "");
        } else if (Constants.EV_XPOSED_LISTENER_REGISTER_OK.equals(event)) {
            editor.putString(KEY_APP_STAGE, "registerListener")
                .putString(KEY_APP_STATUS, "REGISTERED")
                .putLong(KEY_APP_REGISTERED_AT, now);
            if (summary != null && !summary.isEmpty()) {
                editor.putString(KEY_APP_ERROR, "");
            }
        } else if (Constants.EV_XPOSED_LISTENER_REGISTER_FAILED.equals(event)) {
            editor.putString(KEY_APP_STAGE, "registerListener")
                .putString(KEY_APP_STATUS, "FAILED");
        } else if (Constants.EV_XPOSED_SERVICE_BIND.equals(event)) {
            editor.putString(KEY_APP_STAGE, "onServiceBind")
                .putString(KEY_APP_STATUS, "BOUND")
                .putLong(KEY_APP_BOUND_AT, now)
                .putString(KEY_APP_ERROR, "");
        } else if (Constants.EV_XPOSED_SERVICE_DIED.equals(event)) {
            editor.putString(KEY_APP_STAGE, "onServiceDied")
                .putString(KEY_APP_STATUS, "DIED");
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String value(String value) {
        return isEmpty(value) ? "-" : value;
    }
}
