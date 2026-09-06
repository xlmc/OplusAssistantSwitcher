package com.ouhuan.oplusassistant.shared;

/**
 * AssistantLauncher 结构化结果（开发书 7）。
 * SUCCESS 的定义是「系统接受了本次启动请求」，不代表目标 UI 一定已展示。
 */
public enum LaunchResult {
    SUCCESS,
    TARGET_NOT_FOUND,
    TARGET_DISABLED,
    ROLE_MISMATCH,
    RESOLVE_FAILED,
    SECURITY_EXCEPTION,
    BACKGROUND_START_DENIED,
    LAUNCH_EXCEPTION,
    UNKNOWN_ERROR
}
