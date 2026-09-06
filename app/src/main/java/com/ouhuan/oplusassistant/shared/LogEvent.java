package com.ouhuan.oplusassistant.shared;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 轻量日志事件模型。
 * Hook 侧构造为 Map 经广播上报，App 侧还原入库（开发书 8.3 / 8.4）。
 * 禁止携带语音正文、屏幕内容、账户信息、Token 等敏感数据。
 */
public final class LogEvent implements Serializable {

    public String kind = Constants.KIND_CALL;
    public String event = "";
    public String timestamp = "";
    public String androidVersion = "";
    public String colorOsVersion = "";
    public String deviceModel = "";
    public String hookStrategy = "";
    public String hookStatus = "";
    public String systemDefaultAssistant = "";
    public String selectedAssistant = "";
    public String targetPackage = "";
    public String targetComponent = "";
    public String trigger = "";
    public String launchMethod = "";
    public String result = "";
    public String failureCode = "";
    public String exceptionType = "";
    public String exceptionSummary = "";

    public static LogEvent fromMap(Map<String, String> map) {
        LogEvent e = new LogEvent();
        if (map == null) {
            return e;
        }
        e.kind = value(map, Constants.FIELD_KIND, Constants.KIND_CALL);
        e.event = value(map, Constants.FIELD_EVENT, "");
        e.timestamp = value(map, Constants.FIELD_TIMESTAMP, "");
        e.androidVersion = value(map, Constants.FIELD_ANDROID_VERSION, "");
        e.colorOsVersion = value(map, Constants.FIELD_COLOROS_VERSION, "");
        e.deviceModel = value(map, Constants.FIELD_DEVICE_MODEL, "");
        e.hookStrategy = value(map, Constants.FIELD_HOOK_STRATEGY, "");
        e.hookStatus = value(map, Constants.FIELD_HOOK_STATUS, "");
        e.systemDefaultAssistant = value(map, Constants.FIELD_SYSTEM_DEFAULT_ASSISTANT, "");
        e.selectedAssistant = value(map, Constants.FIELD_SELECTED_ASSISTANT, "");
        e.targetPackage = value(map, Constants.FIELD_TARGET_PACKAGE, "");
        e.targetComponent = value(map, Constants.FIELD_TARGET_COMPONENT, "");
        e.trigger = value(map, Constants.FIELD_TRIGGER, "");
        e.launchMethod = value(map, Constants.FIELD_LAUNCH_METHOD, "");
        e.result = value(map, Constants.FIELD_RESULT, "");
        e.failureCode = value(map, Constants.FIELD_FAILURE_CODE, "");
        e.exceptionType = value(map, Constants.FIELD_EXCEPTION_TYPE, "");
        e.exceptionSummary = value(map, Constants.FIELD_EXCEPTION_SUMMARY, "");
        return e;
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(Constants.FIELD_KIND, nullToDash(kind));
        map.put(Constants.FIELD_EVENT, nullToDash(event));
        map.put(Constants.FIELD_TIMESTAMP, nullToDash(timestamp));
        map.put(Constants.FIELD_ANDROID_VERSION, nullToDash(androidVersion));
        map.put(Constants.FIELD_COLOROS_VERSION, nullToDash(colorOsVersion));
        map.put(Constants.FIELD_DEVICE_MODEL, nullToDash(deviceModel));
        map.put(Constants.FIELD_HOOK_STRATEGY, nullToDash(hookStrategy));
        map.put(Constants.FIELD_HOOK_STATUS, nullToDash(hookStatus));
        map.put(Constants.FIELD_SYSTEM_DEFAULT_ASSISTANT, nullToDash(systemDefaultAssistant));
        map.put(Constants.FIELD_SELECTED_ASSISTANT, nullToDash(selectedAssistant));
        map.put(Constants.FIELD_TARGET_PACKAGE, nullToDash(targetPackage));
        map.put(Constants.FIELD_TARGET_COMPONENT, nullToDash(targetComponent));
        map.put(Constants.FIELD_TRIGGER, nullToDash(trigger));
        map.put(Constants.FIELD_LAUNCH_METHOD, nullToDash(launchMethod));
        map.put(Constants.FIELD_RESULT, nullToDash(result));
        map.put(Constants.FIELD_FAILURE_CODE, nullToDash(failureCode));
        map.put(Constants.FIELD_EXCEPTION_TYPE, nullToDash(exceptionType));
        map.put(Constants.FIELD_EXCEPTION_SUMMARY, nullToDash(exceptionSummary));
        return map;
    }

    /** 单条日志的可读文本（日志页「复制单条」使用）。 */
    public String toReadableText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(Constants.KIND_HOOK.equals(kind) ? "HOOK" : "CALL").append("] ")
            .append(event).append(" · ").append(formatTimestamp());
        appendField(sb, "androidVersion", androidVersion);
        appendField(sb, "colorOsVersion", colorOsVersion);
        appendField(sb, "deviceModel", deviceModel);
        appendField(sb, "hookStrategy", hookStrategy);
        appendField(sb, "hookStatus", hookStatus);
        appendField(sb, "systemDefaultAssistant", systemDefaultAssistant);
        appendField(sb, "selectedAssistant", selectedAssistant);
        appendField(sb, "targetPackage", targetPackage);
        appendField(sb, "targetComponent", targetComponent);
        appendField(sb, "trigger", trigger);
        appendField(sb, "launchMethod", launchMethod);
        appendField(sb, "result", result);
        appendField(sb, "failureCode", failureCode);
        appendField(sb, "exceptionType", exceptionType);
        appendField(sb, "exceptionSummary", exceptionSummary);
        return sb.toString();
    }

    public boolean isFailure() {
        if (Constants.KIND_HOOK.equals(kind)) {
            return Constants.EV_HOOK_FAILED.equals(event)
                || Constants.EV_ROM_UNSUPPORTED.equals(event)
                || Constants.EV_HOOK_TARGET_NOT_FOUND.equals(event);
        }
        return !LaunchResult.SUCCESS.name().equals(result);
    }

    public String formatTimestamp() {
        if (timestamp == null || timestamp.isEmpty()) {
            return "-";
        }
        try {
            long ts = Long.parseLong(timestamp);
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(ts));
        } catch (NumberFormatException ex) {
            return timestamp;
        }
    }

    private static void appendField(StringBuilder sb, String name, String value) {
        if (value != null && !value.isEmpty() && !"-".equals(value)) {
            sb.append("\n").append(name).append("=").append(value);
        }
    }

    private static String value(Map<String, String> map, String key, String def) {
        String v = map.get(key);
        return v == null || v.isEmpty() ? def : v;
    }

    private static String nullToDash(String v) {
        return v == null || v.isEmpty() ? "-" : v;
    }
}
