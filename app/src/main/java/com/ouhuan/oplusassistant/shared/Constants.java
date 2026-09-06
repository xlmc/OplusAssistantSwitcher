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

    /** Hook 侧 → App 侧日志广播 Action。 */
    public static final String ACTION_LOG_EVENT = "com.ouhuan.oplusassistant.action.LOG_EVENT";

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
    public static final String EV_SYSTEM_SERVER_READY = "SYSTEM_SERVER_READY";
    public static final String EV_HOOK_CLASS_FOUND = "HOOK_CLASS_FOUND";
    public static final String EV_HOOK_METHOD_FOUND = "HOOK_METHOD_FOUND";
    public static final String EV_HOOK_INSTALLED = "HOOK_INSTALLED";
    public static final String EV_HOOK_FAILED = "HOOK_FAILED";
    public static final String EV_HOOK_TARGET_NOT_FOUND = "HOOK_TARGET_NOT_FOUND";
    public static final String EV_ROM_UNSUPPORTED = "ROM_UNSUPPORTED";

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
