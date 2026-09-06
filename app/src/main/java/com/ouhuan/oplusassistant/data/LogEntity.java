package com.ouhuan.oplusassistant.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.LogEvent;

import java.util.Map;

/**
 * 日志表：App 侧持久化（开发书 8.4）。
 * system_server 触发热路径不直接写库，仅经广播送达后落库。
 */
@Entity(tableName = "log_events")
public class LogEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "kind")
    public String kind = Constants.KIND_CALL;

    @ColumnInfo(name = "event")
    public String event = "";

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "android_version")
    public String androidVersion = "";

    @ColumnInfo(name = "coloros_version")
    public String colorOsVersion = "";

    @ColumnInfo(name = "device_model")
    public String deviceModel = "";

    @ColumnInfo(name = "hook_strategy")
    public String hookStrategy = "";

    @ColumnInfo(name = "hook_status")
    public String hookStatus = "";

    @ColumnInfo(name = "system_default_assistant")
    public String systemDefaultAssistant = "";

    @ColumnInfo(name = "selected_assistant")
    public String selectedAssistant = "";

    @ColumnInfo(name = "target_package")
    public String targetPackage = "";

    @ColumnInfo(name = "target_component")
    public String targetComponent = "";

    @ColumnInfo(name = "trigger")
    public String trigger = "";

    @ColumnInfo(name = "launch_method")
    public String launchMethod = "";

    @ColumnInfo(name = "result")
    public String result = "";

    @ColumnInfo(name = "failure_code")
    public String failureCode = "";

    @ColumnInfo(name = "exception_type")
    public String exceptionType = "";

    @ColumnInfo(name = "exception_summary")
    public String exceptionSummary = "";

    @ColumnInfo(name = "is_failure")
    public boolean isFailure;

    public static LogEntity fromMap(Map<String, String> map) {
        LogEvent model = LogEvent.fromMap(map);
        LogEntity entity = new LogEntity();
        entity.kind = model.kind;
        entity.event = model.event;
        try {
            entity.timestamp = Long.parseLong(model.timestamp);
        } catch (NumberFormatException ex) {
            entity.timestamp = System.currentTimeMillis();
        }
        entity.androidVersion = model.androidVersion;
        entity.colorOsVersion = model.colorOsVersion;
        entity.deviceModel = model.deviceModel;
        entity.hookStrategy = model.hookStrategy;
        entity.hookStatus = model.hookStatus;
        entity.systemDefaultAssistant = model.systemDefaultAssistant;
        entity.selectedAssistant = model.selectedAssistant;
        entity.targetPackage = model.targetPackage;
        entity.targetComponent = model.targetComponent;
        entity.trigger = model.trigger;
        entity.launchMethod = model.launchMethod;
        entity.result = model.result;
        entity.failureCode = model.failureCode;
        entity.exceptionType = model.exceptionType;
        entity.exceptionSummary = model.exceptionSummary;
        entity.isFailure = model.isFailure();
        return entity;
    }

    public LogEvent toModel() {
        LogEvent model = new LogEvent();
        model.kind = kind;
        model.event = event;
        model.timestamp = String.valueOf(timestamp);
        model.androidVersion = androidVersion;
        model.colorOsVersion = colorOsVersion;
        model.deviceModel = deviceModel;
        model.hookStrategy = hookStrategy;
        model.hookStatus = hookStatus;
        model.systemDefaultAssistant = systemDefaultAssistant;
        model.selectedAssistant = selectedAssistant;
        model.targetPackage = targetPackage;
        model.targetComponent = targetComponent;
        model.trigger = trigger;
        model.launchMethod = launchMethod;
        model.result = result;
        model.failureCode = failureCode;
        model.exceptionType = exceptionType;
        model.exceptionSummary = exceptionSummary;
        return model;
    }
}
