package com.ouhuan.oplusassistant.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

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
                prefs.edit().putString(KEY_ENTRIES, joinRecords(records)).apply();
            }
        } catch (Throwable ignored) {
            // 诊断记录不能影响主链路；Logcat 由调用方负责兜底。
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

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String value(String value) {
        return isEmpty(value) ? "-" : value;
    }
}
