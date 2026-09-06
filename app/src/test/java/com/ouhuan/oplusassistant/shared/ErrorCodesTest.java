package com.ouhuan.oplusassistant.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** 开发书 12 标准错误码完整性检查。 */
public class ErrorCodesTest {

    @Test
    public void allErrorCodesAreDefined() {
        assertEquals(14, ErrorCodes.ALL.length);
        assertEquals("HOOK_NOT_ACTIVE", ErrorCodes.HOOK_NOT_ACTIVE);
        assertEquals("HOOK_TARGET_NOT_FOUND", ErrorCodes.HOOK_TARGET_NOT_FOUND);
        assertEquals("ROM_UNSUPPORTED", ErrorCodes.ROM_UNSUPPORTED);
        assertEquals("NO_SELECTED_ASSISTANT", ErrorCodes.NO_SELECTED_ASSISTANT);
        assertEquals("ASSISTANT_NOT_INSTALLED", ErrorCodes.ASSISTANT_NOT_INSTALLED);
        assertEquals("ASSISTANT_COMPONENT_MISSING", ErrorCodes.ASSISTANT_COMPONENT_MISSING);
        assertEquals("ASSISTANT_SERVICE_DISABLED", ErrorCodes.ASSISTANT_SERVICE_DISABLED);
        assertEquals("ASSISTANT_NOT_ELIGIBLE", ErrorCodes.ASSISTANT_NOT_ELIGIBLE);
        assertEquals("ROLE_MISMATCH", ErrorCodes.ROLE_MISMATCH);
        assertEquals("RESOLVE_FAILED", ErrorCodes.RESOLVE_FAILED);
        assertEquals("SECURITY_EXCEPTION", ErrorCodes.SECURITY_EXCEPTION);
        assertEquals("BACKGROUND_START_DENIED", ErrorCodes.BACKGROUND_START_DENIED);
        assertEquals("LAUNCH_EXCEPTION", ErrorCodes.LAUNCH_EXCEPTION);
        assertEquals("UNKNOWN_ERROR", ErrorCodes.UNKNOWN_ERROR);
    }

    @Test
    public void allErrorCodesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (String code : ErrorCodes.ALL) {
            assertTrue("duplicate error code: " + code, seen.add(code));
        }
    }
}
