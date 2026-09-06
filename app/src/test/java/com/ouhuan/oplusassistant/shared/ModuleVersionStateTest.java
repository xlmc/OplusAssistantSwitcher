package com.ouhuan.oplusassistant.shared;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** App 与 system_server 模块版本比较（Issue #1 P1）。 */
public class ModuleVersionStateTest {

    @Test
    public void matchingNameAndCodeIsSynchronized() {
        ModuleVersionState state = new ModuleVersionState("0.1.0", 1L, 100L);

        assertEquals(ModuleVersionState.Comparison.MATCH,
            state.compareTo("0.1.0", 1L));
    }

    @Test
    public void changedNameOrCodeRequiresReload() {
        ModuleVersionState state = new ModuleVersionState("0.1.0", 1L, 100L);

        assertEquals(ModuleVersionState.Comparison.MISMATCH,
            state.compareTo("0.1.1", 2L));
        assertEquals(ModuleVersionState.Comparison.MISMATCH,
            state.compareTo("0.1.0", 2L));
    }

    @Test
    public void incompleteStateNeverReportsMatch() {
        ModuleVersionState unknownName = new ModuleVersionState("", 1L, 100L);
        ModuleVersionState unknownCode = new ModuleVersionState("0.1.0",
            ModuleVersionState.UNKNOWN_VERSION_CODE, 100L);

        assertEquals(ModuleVersionState.Comparison.UNKNOWN,
            unknownName.compareTo("0.1.0", 1L));
        assertEquals(ModuleVersionState.Comparison.UNKNOWN,
            unknownCode.compareTo("0.1.0", 1L));
    }
}
