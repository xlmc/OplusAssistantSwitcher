package com.ouhuan.oplusassistant.shared;

/**
 * system_server ↔ 模块 App 运行态 Binder 协议（Issue #1 P0）。
 * 仅包含 class-loader 中立的字符串、数字和 Bundle 事务常量。
 */
public final class RuntimeStatusContract {

    private RuntimeStatusContract() {
    }

    public static final int PROTOCOL_VERSION = 1;

    /** App 侧导出的显式 Binder service。 */
    public static final String SERVICE_CLASS =
        "com.ouhuan.oplusassistant.app.RuntimeStatusService";
    public static final String SERVICE_DESCRIPTOR =
        "com.ouhuan.oplusassistant.runtime.IRuntimeStatus";
    public static final String CALLBACK_DESCRIPTOR =
        "com.ouhuan.oplusassistant.runtime.IRuntimeStatusCallback";

    /** App service 事务。 */
    public static final int TRANSACTION_REGISTER_CALLBACK = 1;
    public static final int TRANSACTION_PUSH_STATE = 2;
    public static final int TRANSACTION_PUSH_EVENT = 3;

    /** system_server 回调事务。 */
    public static final int TRANSACTION_PING = 1;

    public static final String CHANNEL_WAITING = "WAITING";
    public static final String CHANNEL_READY = "READY";
    public static final String CHANNEL_FAILED = "FAILED";
    public static final String CHANNEL_DISCONNECTED = "DISCONNECTED";

    public static final String KEY_PROTOCOL_VERSION = "runtime_protocol_version";
    public static final String KEY_PROCESS_NAME = "runtime_process_name";
    public static final String KEY_MODULE_VERSION_NAME = Constants.STATE_MODULE_VERSION_NAME;
    public static final String KEY_MODULE_VERSION_CODE = Constants.STATE_MODULE_VERSION_CODE;
    public static final String KEY_MODULE_LOADED_AT = Constants.STATE_MODULE_LOADED_AT;
    public static final String KEY_FRAMEWORK_NAME = "runtime_framework_name";
    public static final String KEY_FRAMEWORK_VERSION = "runtime_framework_version";
    public static final String KEY_FRAMEWORK_VERSION_CODE = "runtime_framework_version_code";
    public static final String KEY_FRAMEWORK_API = "runtime_framework_api";
    public static final String KEY_FRAMEWORK_PROPERTIES = "runtime_framework_properties";
    public static final String KEY_FRAMEWORK_TARGET_PROCESS = "runtime_target_process";
    public static final String KEY_FRAMEWORK_TARGET_STATE = "runtime_target_state";
    public static final String KEY_FRAMEWORK_TARGET_VERSION_CODE =
        "runtime_target_loaded_version_code";
    public static final String KEY_FRAMEWORK_TARGET_PID = "runtime_target_pid";
    public static final String KEY_HOOK_STRATEGY = "runtime_hook_strategy";
    public static final String KEY_HOOK_STAGE = "runtime_hook_stage";
    public static final String KEY_HOOK_STATUS = "runtime_hook_status";
    public static final String KEY_CONTEXT_STATE = "runtime_context_state";
    public static final String KEY_CHANNEL_STATE = "runtime_channel_state";
    public static final String KEY_MODULE_UID = "runtime_module_uid";
    public static final String KEY_PEER_UID = "runtime_peer_uid";
    public static final String KEY_PEER_PROCESS = "runtime_peer_process";
    public static final String KEY_LAST_EVENT = "runtime_last_event";
    public static final String KEY_LAST_EVENT_AT = "runtime_last_event_at";
    public static final String KEY_EVENT_SEQUENCE = "runtime_event_sequence";
    public static final String KEY_POWER_ASSIST_MATCHED_AT =
        "runtime_power_assist_matched_at";
    public static final String KEY_POWER_ASSIST_STATUS = "runtime_power_assist_status";
    public static final String KEY_STATE_TIMESTAMP = Constants.STATE_TIMESTAMP;
    public static final String KEY_CURRENT_NAME = Constants.STATE_CURRENT_NAME;
    public static final String KEY_CURRENT_PACKAGE = Constants.STATE_CURRENT_PACKAGE;
    public static final String KEY_CURRENT_COMPONENT = Constants.STATE_CURRENT_COMPONENT;
    public static final String KEY_CURRENT_SOURCE = Constants.STATE_CURRENT_SOURCE;
    public static final String KEY_ROLE_HOLDER = Constants.STATE_ROLE_HOLDER;
    public static final String KEY_VOICE_INTERACTION_SERVICE =
        Constants.STATE_VOICE_INTERACTION_SERVICE;
    public static final String KEY_CANDIDATES = Constants.STATE_CANDIDATES;
    public static final String KEY_CONFIG_KNOWN = "runtime_config_known";
    public static final String KEY_CONFIG_ENABLED = "runtime_config_enabled";
    public static final String KEY_CONFIG_DETAIL = "runtime_config_detail";
    public static final String KEY_CONFIG_SELECTED_PACKAGE = "runtime_config_selected_package";
    public static final String KEY_CONFIG_SELECTED_COMPONENT = "runtime_config_selected_component";
    public static final String KEY_CONFIG_UPDATED_AT = "runtime_config_updated_at";
    public static final String KEY_PING_STATUS = "runtime_ping_status";
    public static final String KEY_PING_AT = "runtime_ping_at";
    public static final String KEY_PING_SUMMARY = "runtime_ping_summary";

    private static final String PREFIX = "runtime_";

    /** 用于避免把运行态 Bundle 中的普通键误当成日志字段。 */
    public static String key(String suffix) {
        return PREFIX + suffix;
    }
}
