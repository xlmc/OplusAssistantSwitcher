package com.ouhuan.oplusassistant.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** 日志事件模型序列化与失败判定（开发书 8.3）。 */
public class LogEventTest {

    @Test
    public void mapRoundTripKeepsAllFields() {
        LogEvent event = new LogEvent();
        event.kind = Constants.KIND_CALL;
        event.event = Constants.EV_LAUNCH_ACCEPTED;
        event.timestamp = "1725577601000";
        event.androidVersion = "Android 16 (API 36)";
        event.colorOsVersion = "16.0.0";
        event.deviceModel = "OPPO test";
        event.hookStrategy = Constants.HOOK_STRATEGY_COLOROS16;
        event.hookStatus = Constants.EV_HOOK_INSTALLED;
        event.systemDefaultAssistant = "小布助手（com.heytap.speechassist）";
        event.selectedAssistant = "com.example.assistant";
        event.targetPackage = "com.example.assistant";
        event.targetComponent = "com.example.assistant/.AssistActivity";
        event.trigger = Constants.TRIGGER_POWER_ASSIST_0X3F3;
        event.launchMethod = Constants.LAUNCH_METHOD_ASSIST;
        event.result = LaunchResult.SUCCESS.name();

        Map<String, String> map = event.toMap();
        assertEquals(18, map.size());
        assertEquals(Constants.TRIGGER_POWER_ASSIST_0X3F3,
            map.get(Constants.FIELD_TRIGGER));

        LogEvent restored = LogEvent.fromMap(new HashMap<>(map));
        assertEquals(event.event, restored.event);
        assertEquals(event.timestamp, restored.timestamp);
        assertEquals(event.targetComponent, restored.targetComponent);
        assertEquals(event.result, restored.result);
        assertEquals(event.trigger, restored.trigger);
        assertEquals(event.launchMethod, restored.launchMethod);
    }

    @Test
    public void successCallIsNotFailure() {
        LogEvent event = new LogEvent();
        event.kind = Constants.KIND_CALL;
        event.event = Constants.EV_LAUNCH_ACCEPTED;
        event.result = LaunchResult.SUCCESS.name();
        assertFalse(event.isFailure());
    }

    @Test
    public void failedCallIsFailure() {
        LogEvent event = new LogEvent();
        event.kind = Constants.KIND_CALL;
        event.event = Constants.EV_TARGET_NOT_FOUND;
        event.result = LaunchResult.TARGET_NOT_FOUND.name();
        event.failureCode = ErrorCodes.ASSISTANT_NOT_INSTALLED;
        assertTrue(event.isFailure());
    }

    @Test
    public void hookFailureEventsAreFailure() {
        LogEvent event = new LogEvent();
        event.kind = Constants.KIND_HOOK;
        event.event = Constants.EV_HOOK_FAILED;
        assertTrue(event.isFailure());

        LogEvent unsupported = new LogEvent();
        unsupported.kind = Constants.KIND_HOOK;
        unsupported.event = Constants.EV_ROM_UNSUPPORTED;
        assertTrue(unsupported.isFailure());

        LogEvent ok = new LogEvent();
        ok.kind = Constants.KIND_HOOK;
        ok.event = Constants.EV_HOOK_INSTALLED;
        assertFalse(ok.isFailure());
    }
}
