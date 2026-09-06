package com.ouhuan.oplusassistant.shared;

/**
 * 助手选择页候选模型：仅包含已安装、组件启用、可解析且可实际调用的
 * 第三方语音助手（开发书 5.2 / 9）。
 */
public final class AssistantCandidate {

    public final String packageName;
    public final String label;
    /** 可实际调用的 Assistant 入口组件（flattenToString）。 */
    public final String componentName;
    /** 入口类型：Constants.LAUNCH_METHOD_*。 */
    public final String launchMethod;
    public final boolean hasVoiceInteractionService;

    public AssistantCandidate(String packageName,
                              String label,
                              String componentName,
                              String launchMethod,
                              boolean hasVoiceInteractionService) {
        this.packageName = packageName;
        this.label = label;
        this.componentName = componentName;
        this.launchMethod = launchMethod;
        this.hasVoiceInteractionService = hasVoiceInteractionService;
    }
}
