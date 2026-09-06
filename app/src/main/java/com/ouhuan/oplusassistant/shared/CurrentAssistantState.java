package com.ouhuan.oplusassistant.shared;

/**
 * 「当前手机实际助手」状态模型（Issue #1 评论 4：CurrentOplusAssistant）。
 *
 * 由 system_server 侧 AssistantResolver 以系统上下文解析（App 侧普通
 * PackageManager 与 Android 11+ package visibility 不作为能力边界），
 * 经状态广播回传 App 持久化，是首页「当前系统默认助手」卡主结论的
 * 最高可信来源。
 *
 * 标准 ROLE_ASSISTANT / VoiceInteractionService 原始值
 * （AndroidAssistantRoleState）仅随载荷附带，供诊断页展示，
 * 不决定首页主结论。
 */
public final class CurrentAssistantState {

    /** 解析来源：系统电源键原始目标组件（最高可信，厂商电源键配置）。 */
    public static final String SOURCE_POWER_KEY = "POWER_KEY_COMPONENT";
    /** 解析来源：OEM（coloros/heytap/oplus/oppo/oneplus 前缀）语音服务。 */
    public static final String SOURCE_OEM_VOICE_SERVICE = "OEM_VOICE_SERVICE";
    /** 解析来源：标准 ROLE_ASSISTANT 持有者。 */
    public static final String SOURCE_ROLE_HOLDER = "ROLE_HOLDER";
    /** 系统未暴露任何助手信息。 */
    public static final String SOURCE_NONE = "NONE";

    public final String displayName;
    public final String packageName;
    public final String componentName;
    public final String source;
    public final boolean resolvedFromSystemServer;
    public final String roleHolderPackage;
    public final String voiceInteractionService;
    public final long timestamp;

    public CurrentAssistantState(String displayName,
                                 String packageName,
                                 String componentName,
                                 String source,
                                 boolean resolvedFromSystemServer,
                                 String roleHolderPackage,
                                 String voiceInteractionService,
                                 long timestamp) {
        this.displayName = displayName == null ? "" : displayName;
        this.packageName = packageName == null ? "" : packageName;
        this.componentName = componentName == null ? "" : componentName;
        this.source = source == null || source.isEmpty() ? SOURCE_NONE : source;
        this.resolvedFromSystemServer = resolvedFromSystemServer;
        this.roleHolderPackage = roleHolderPackage == null ? "" : roleHolderPackage;
        this.voiceInteractionService = voiceInteractionService == null ? "" : voiceInteractionService;
        this.timestamp = timestamp;
    }
}
