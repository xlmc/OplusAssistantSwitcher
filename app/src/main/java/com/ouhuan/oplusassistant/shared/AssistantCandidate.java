package com.ouhuan.oplusassistant.shared;

/**
 * 助手选择页候选模型：仅包含已安装、组件启用、可解析且可实际调用的
 * 第三方语音助手（开发书 5.2 / 9，Issue #1 收紧后的规则）。
 * 入选前提：包内声明 VoiceInteractionService 且该服务要求
 * BIND_VOICE_INTERACTION 权限；ACTION_ASSIST 仅作为可调用入口的辅助信号。
 */
public final class AssistantCandidate {

    public final String packageName;
    public final String label;
    /** 可实际调用的 Assistant 入口组件（flattenToString）。 */
    public final String componentName;
    /** 入口类型：Constants.LAUNCH_METHOD_*。 */
    public final String launchMethod;
    public final boolean hasVoiceInteractionService;
    /** 资格判定依据（供诊断页展示，如 "VoiceInteractionService + BIND_VOICE_INTERACTION"）。 */
    public final String eligibilitySource;

    public AssistantCandidate(String packageName,
                              String label,
                              String componentName,
                              String launchMethod,
                              boolean hasVoiceInteractionService) {
        this(packageName, label, componentName, launchMethod,
            hasVoiceInteractionService, null);
    }

    public AssistantCandidate(String packageName,
                              String label,
                              String componentName,
                              String launchMethod,
                              boolean hasVoiceInteractionService,
                              String eligibilitySource) {
        this.packageName = packageName;
        this.label = label;
        this.componentName = componentName;
        this.launchMethod = launchMethod;
        this.hasVoiceInteractionService = hasVoiceInteractionService;
        this.eligibilitySource = eligibilitySource == null || eligibilitySource.isEmpty()
            ? "ACTION_ASSIST_VERIFIED"
            : eligibilitySource;
    }
}
