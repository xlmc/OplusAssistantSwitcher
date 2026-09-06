package com.ouhuan.oplusassistant.shared;

/**
 * 标准错误码（开发书 12）。
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    public static final String HOOK_NOT_ACTIVE = "HOOK_NOT_ACTIVE";
    public static final String HOOK_TARGET_NOT_FOUND = "HOOK_TARGET_NOT_FOUND";
    public static final String ROM_UNSUPPORTED = "ROM_UNSUPPORTED";
    public static final String NO_SELECTED_ASSISTANT = "NO_SELECTED_ASSISTANT";
    public static final String ASSISTANT_NOT_INSTALLED = "ASSISTANT_NOT_INSTALLED";
    public static final String ASSISTANT_COMPONENT_MISSING = "ASSISTANT_COMPONENT_MISSING";
    public static final String ASSISTANT_SERVICE_DISABLED = "ASSISTANT_SERVICE_DISABLED";
    public static final String ASSISTANT_NOT_ELIGIBLE = "ASSISTANT_NOT_ELIGIBLE";
    public static final String ROLE_MISMATCH = "ROLE_MISMATCH";
    public static final String RESOLVE_FAILED = "RESOLVE_FAILED";
    public static final String SECURITY_EXCEPTION = "SECURITY_EXCEPTION";
    public static final String BACKGROUND_START_DENIED = "BACKGROUND_START_DENIED";
    public static final String LAUNCH_EXCEPTION = "LAUNCH_EXCEPTION";
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";

    public static final String[] ALL = {
        HOOK_NOT_ACTIVE,
        HOOK_TARGET_NOT_FOUND,
        ROM_UNSUPPORTED,
        NO_SELECTED_ASSISTANT,
        ASSISTANT_NOT_INSTALLED,
        ASSISTANT_COMPONENT_MISSING,
        ASSISTANT_SERVICE_DISABLED,
        ASSISTANT_NOT_ELIGIBLE,
        ROLE_MISMATCH,
        RESOLVE_FAILED,
        SECURITY_EXCEPTION,
        BACKGROUND_START_DENIED,
        LAUNCH_EXCEPTION,
        UNKNOWN_ERROR
    };
}
