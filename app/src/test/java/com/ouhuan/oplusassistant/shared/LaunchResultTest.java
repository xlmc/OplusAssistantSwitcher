package com.ouhuan.oplusassistant.shared;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 开发书 7 LaunchResult 结构化结果完整性检查。 */
public class LaunchResultTest {

    @Test
    public void allResultsAreDefined() {
        assertEquals(9, LaunchResult.values().length);
        assertEquals("SUCCESS", LaunchResult.SUCCESS.name());
        assertEquals("TARGET_NOT_FOUND", LaunchResult.TARGET_NOT_FOUND.name());
        assertEquals("TARGET_DISABLED", LaunchResult.TARGET_DISABLED.name());
        assertEquals("ROLE_MISMATCH", LaunchResult.ROLE_MISMATCH.name());
        assertEquals("RESOLVE_FAILED", LaunchResult.RESOLVE_FAILED.name());
        assertEquals("SECURITY_EXCEPTION", LaunchResult.SECURITY_EXCEPTION.name());
        assertEquals("BACKGROUND_START_DENIED", LaunchResult.BACKGROUND_START_DENIED.name());
        assertEquals("LAUNCH_EXCEPTION", LaunchResult.LAUNCH_EXCEPTION.name());
        assertEquals("UNKNOWN_ERROR", LaunchResult.UNKNOWN_ERROR.name());
    }
}
