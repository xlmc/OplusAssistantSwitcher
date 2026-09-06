package com.ouhuan.oplusassistant.shared;

/**
 * 欧唤全局常量。本类必须保持纯 Java（仅 java.* 依赖），
 * 因为 shared 层会被同时加载进 system_server（Hook 侧）与模块 App。
 */
public final class Constants {

    private Constants() {
    }

    /** 模块应用包名（java_init.list / 广播目标均以此为准）。 */
    public static final String MODULE_PACKAGE = "com.ouhuan.oplusassistant";

    /** 未知版本号统一使用的哨兵值。 */
    public static final long UNKNOWN_VERSION_CODE = -1L;

    /** Hook 侧 → App 侧日志广播 Action。 */
    public static final String ACTION_LOG_EVENT = "com.ouhuan.oplusassistant.action.LOG_EVENT";

    /** Hook 侧 → App 侧状态广播 Action（CurrentOplusAssistant 回传，Issue #1 评论 4）。 */
    public static final String ACTION_STATE_REPORT = "com.ouhuan.oplusassistant.action.STATE_REPORT";

    /** 回传通道发送方权限（signature 级）：system uid 由 AMS 组件检查短路放行，
     * 第三方应用不持有本签名权限、无法投递（Issue #1 P0 修复）。 */
    public static final String PERMISSION_REPORT = "com.ouhuan.oplusassistant.permission.REPORT";

    /** 回传广播的显式目标组件（跨 UID 场景下避免 intent-filter 解析变数）。 */
    public static final String RECEIVER_CLASS = "com.ouhuan.oplusassistant.data.LogEntryReceiver";

    /** 状态广播字段：当前实际助手（system_server 解析）。 */
    public static final String STATE_CURRENT_NAME = "state_current_name";
    public static final String STATE_CURRENT_PACKAGE = "state_current_package";
    public static final String STATE_CURRENT_COMPONENT = "state_current_component";
    public static final String STATE_CURRENT_SOURCE = "state_current_source";
    public static final String STATE_FROM_SYSTEM_SERVER = "state_from_system_server";
    public static final String STATE_ROLE_HOLDER = "state_role_holder";
    public static final String STATE_VOICE_INTERACTION_SERVICE = "state_voice_interaction_service";
    public static final String STATE_TIMESTAMP = "state_timestamp";
    /** system_server 实际加载的模块版本（用于区分 App 已更新与 Hook 已重载）。 */
    public static final String STATE_MODULE_VERSION_NAME = "state_module_version_name";
    public static final String STATE_MODULE_VERSION_CODE = "state_module_version_code";
    public static final String STATE_MODULE_LOADED_AT = "state_module_loaded_at";
    /** 状态广播附加：system_server 侧扫描的候选列表（StringArrayList，序列化条目）。 */
    public static final String STATE_CANDIDATES = "state_candidates";

    /** OEM 助手组件包名前缀（用于 system_server 侧发现小布等内置语音服务；非固定包名白名单）。 */
    public static final String[] OEM_ASSISTANT_PACKAGE_PREFIXES = {
        "com.coloros.",
        "com.heytap.",
        "com.oplus.",
        "com.oppo.",
        "com.oneplus."
    };

    /** Remote Preferences 分组名：App 侧写入，Hook 侧只读同步。 */
    public static final String PREFS_GROUP = "ouhuan_config";

    public static final String KEY_MODULE_ENABLED = "module_enabled";
    public static final String KEY_DETAIL_DIAGNOSTICS = "detail_diagnostics";
    public static final String KEY_SELECTED_PACKAGE = "selected_package";
    public static final String KEY_SELECTED_COMPONENT = "selected_component";

    /** 0.5 秒电源键助手唤醒事件标识（日志 trigger 字段取值，见开发书 8.3）。 */
    public static final String TRIGGER_POWER_ASSIST_0X3F3 = "POWER_ASSIST_0X3F3";

    /** V1 唯一 Hook 策略名。 */
    public static final String HOOK_STRATEGY_COLOROS16 = "ColorOS16";

    /** Android 标准 Assistant 入口。 */
    public static final String VIS_SERVICE_INTERFACE = "android.service.voice.VoiceInteractionService";
    public static final String PERM_BIND_VOICE_INTERACTION = "android.permission.BIND_VOICE_INTERACTION";

    /** ColorOS 16 system_server 内的目标 Hook 点（厂商实现细节，允许 ROM 更新后失效）。 */
    public static final String HOOK_CLASS = "com.android.server.policy.PhoneWindowManagerExtImpl$OplusSpeechHandler";
    public static final String HOOK_METHOD = "handleMessage";
    public static final int MSG_POWER_ASSIST_0X3F3 = 0x3F3;

    /** V1 可选列表排除的 OPlus 内置助手（第三方列表定位，见开发书 5.2）。 */
    public static final String[] BUILT_IN_ASSISTANT_PACKAGES = {
        "com.heytap.speechassist",
        "com.oppo.voiceassist",
        "com.oneplus.assistant"
    };

    /** 日志事件类别：Hook 生命周期。 */
    public static final String KIND_HOOK = "hook";
    /** 日志事件类别：调用链路。 */
    public static final String KIND_CALL = "call";

    /** Hook 日志事件（开发书 8.2）。 */
    public static final String EV_MODULE_LOADED = "MODULE_LOADED";
    public static final String EV_SYSTEM_SERVER_STARTING = "SYSTEM_SERVER_STARTING";
    public static final String EV_SYSTEM_CONTEXT_READY = "SYSTEM_CONTEXT_READY";
    public static final String EV_SYSTEM_CONTEXT_UNAVAILABLE = "SYSTEM_CONTEXT_UNAVAILABLE";
    public static final String EV_AMS_SYSTEM_READY = "AMS_SYSTEM_READY";
    public static final String EV_SYSTEM_SERVER_READY = "SYSTEM_SERVER_READY";
    public static final String EV_HOOK_CLASS_FOUND = "HOOK_CLASS_FOUND";
    public static final String EV_HOOK_METHOD_FOUND = "HOOK_METHOD_FOUND";
    public static final String EV_HOOK_CLASS_NOT_FOUND = "HOOK_CLASS_NOT_FOUND";
    public static final String EV_HOOK_METHOD_NOT_FOUND = "HOOK_METHOD_NOT_FOUND";
    public static final String EV_HOOK_INSTALLED = "HOOK_INSTALLED";
    public static final String EV_HOOK_FAILED = "HOOK_FAILED";
    public static final String EV_HOOK_INSTALL_FAILED = "HOOK_INSTALL_FAILED";
    public static final String EV_HOOK_TARGET_NOT_FOUND = "HOOK_TARGET_NOT_FOUND";
    public static final String EV_ROM_UNSUPPORTED = "ROM_UNSUPPORTED";
    public static final String EV_STATE_CHANNEL_READY = "STATE_CHANNEL_READY";
    public static final String EV_STATE_CHANNEL_FAILED = "STATE_CHANNEL_FAILED";
    /** App ↔ LSPosed/XposedService 生命周期事件。 */
    public static final String EV_APP_ON_CREATE = "APP_ON_CREATE";
    public static final String EV_XPOSED_LISTENER_REGISTER_BEGIN =
        "XPOSED_LISTENER_REGISTER_BEGIN";
    public static final String EV_XPOSED_LISTENER_REGISTER_OK =
        "XPOSED_LISTENER_REGISTER_OK";
    public static final String EV_XPOSED_LISTENER_REGISTER_FAILED =
        "XPOSED_LISTENER_REGISTER_FAILED";
    public static final String EV_XPOSED_SERVICE_BIND = "XPOSED_SERVICE_BIND";
    public static final String EV_XPOSED_SERVICE_DIED = "XPOSED_SERVICE_DIED";
    public static final String EV_REMOTE_PREFS_OPEN_OK = "REMOTE_PREFS_OPEN_OK";
    public static final String EV_REMOTE_PREFS_OPEN_FAILED = "REMOTE_PREFS_OPEN_FAILED";
    public static final String EV_REMOTE_PREFS_WRITE_ENABLED_OK =
        "REMOTE_PREFS_WRITE_ENABLED_OK";
    public static final String EV_REMOTE_PREFS_WRITE_ENABLED_FAILED =
        "REMOTE_PREFS_WRITE_ENABLED_FAILED";
    public static final String EV_REMOTE_PREFS_WRITE_OK = "REMOTE_PREFS_WRITE_OK";
    public static final String EV_REMOTE_PREFS_WRITE_FAILED = "REMOTE_PREFS_WRITE_FAILED";
    public static final String EV_CONFIG_RECONCILE_BEGIN = "CONFIG_RECONCILE_BEGIN";
    public static final String EV_CONFIG_RECONCILE_OK = "CONFIG_RECONCILE_OK";
    public static final String EV_CONFIG_RECONCILE_FAILED = "CONFIG_RECONCILE_FAILED";
    /** system_server 运行态 Binder 握手事件。 */
    public static final String EV_RUNTIME_BINDER_BIND_BEGIN = "RUNTIME_BINDER_BIND_BEGIN";
    public static final String EV_RUNTIME_BINDER_BIND_OK = "RUNTIME_BINDER_BIND_OK";
    public static final String EV_RUNTIME_BINDER_BIND_FAILED = "RUNTIME_BINDER_BIND_FAILED";
    public static final String EV_RUNTIME_BINDER_PING_BEGIN = "RUNTIME_BINDER_PING_BEGIN";
    public static final String EV_RUNTIME_BINDER_PING_OK = "RUNTIME_BINDER_PING_OK";
    public static final String EV_RUNTIME_BINDER_PING_FAILED = "RUNTIME_BINDER_PING_FAILED";
    /** system_server 读取 RemotePreferences 的结果。 */
    public static final String EV_CONFIG_READ = "CONFIG_READ";
    public static final String EV_CONFIG_READ_FAILED = "CONFIG_READ_FAILED";
    public static final String EV_RESOLVER_QUERY_FAILED = "RESOLVER_QUERY_FAILED";
    /** 精确标识 0x3F3，保留旧名称以兼容已有日志和测试。 */
    public static final String EV_POWER_ASSIST_0X3F3_MATCHED =
        "POWER_ASSIST_0X3F3_MATCHED";
    public static final String EV_POWER_ASSIST_EVENT_MATCHED = "POWER_ASSIST_EVENT_MATCHED";
    public static final String EV_POWER_ASSIST_NOT_MATCHED = "POWER_ASSIST_NOT_MATCHED";

    /** 调用日志事件（开发书 8.2）。 */
    public static final String EV_POWER_ASSIST_TRIGGERED = "POWER_ASSIST_TRIGGERED";
    public static final String EV_TARGET_RESOLVED = "TARGET_RESOLVED";
    public static final String EV_LAUNCH_REQUESTED = "LAUNCH_REQUESTED";
    public static final String EV_LAUNCH_ACCEPTED = "LAUNCH_ACCEPTED";
    public static final String EV_TARGET_NOT_FOUND = "TARGET_NOT_FOUND";
    public static final String EV_RESOLVE_FAILED = "RESOLVE_FAILED";
    public static final String EV_SECURITY_EXCEPTION = "SECURITY_EXCEPTION";
    public static final String EV_LAUNCH_EXCEPTION = "LAUNCH_EXCEPTION";

    /** 启动方式标记（日志 launchMethod 字段）。 */
    public static final String LAUNCH_METHOD_ASSIST = "ACTION_ASSIST";
    public static final String LAUNCH_METHOD_VOICE_COMMAND = "ACTION_VOICE_COMMAND";

    /** 日志字段名（开发书 8.3 规定的字段集合，广播 extras 与数据库列共用）。 */
    public static final String FIELD_KIND = "kind";
    public static final String FIELD_EVENT = "event";
    public static final String FIELD_TIMESTAMP = "timestamp";
    public static final String FIELD_ANDROID_VERSION = "androidVersion";
    public static final String FIELD_COLOROS_VERSION = "colorOsVersion";
    public static final String FIELD_DEVICE_MODEL = "deviceModel";
    public static final String FIELD_HOOK_STRATEGY = "hookStrategy";
    public static final String FIELD_HOOK_STATUS = "hookStatus";
    public static final String FIELD_SYSTEM_DEFAULT_ASSISTANT = "systemDefaultAssistant";
    public static final String FIELD_SELECTED_ASSISTANT = "selectedAssistant";
    public static final String FIELD_TARGET_PACKAGE = "targetPackage";
    public static final String FIELD_TARGET_COMPONENT = "targetComponent";
    public static final String FIELD_TRIGGER = "trigger";
    public static final String FIELD_LAUNCH_METHOD = "launchMethod";
    public static final String FIELD_RESULT = "result";
    public static final String FIELD_FAILURE_CODE = "failureCode";
    public static final String FIELD_EXCEPTION_TYPE = "exceptionType";
    public static final String FIELD_EXCEPTION_SUMMARY = "exceptionSummary";
}
