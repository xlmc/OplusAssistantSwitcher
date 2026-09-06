package com.ouhuan.oplusassistant.shared;

/**
 * 当前系统默认助手状态（开发书 5.1 SystemAssistantState）。
 * UI 显示必须来自系统实时状态，不允许用模块上次选择冒充。
 * source 标记检测来源，用于区分标准 Android 状态与 ColorOS 电源键目标。
 */
public final class SystemAssistantState {

    /** 检测来源：ROLE_ASSISTANT 持有者。 */
    public static final String SOURCE_ROLE = "ROLE_ASSISTANT";
    /** 检测来源：VoiceInteractionService 设置。 */
    public static final String SOURCE_VIS = "VOICE_INTERACTION_SERVICE";
    /** 检测来源：电源键助手组件（Settings.Secure assistant）。 */
    public static final String SOURCE_ASSIST_COMPONENT = "ASSIST_COMPONENT";
    /** 检测来源：全部不可用。 */
    public static final String SOURCE_NONE = "NONE";

    public final String roleHolderPackage;
    public final String voiceInteractionService;
    public final String assistComponent;
    public final String label;
    public final boolean isAvailable;
    public final String source;

    public SystemAssistantState(String roleHolderPackage,
                                String voiceInteractionService,
                                String label,
                                boolean isAvailable) {
        this(roleHolderPackage, voiceInteractionService, null, label, isAvailable);
    }

    public SystemAssistantState(String roleHolderPackage,
                                String voiceInteractionService,
                                String assistComponent,
                                String label,
                                boolean isAvailable) {
        this(roleHolderPackage, voiceInteractionService, assistComponent, label, isAvailable,
            isAvailable ? primarySource(roleHolderPackage, assistComponent) : SOURCE_NONE);
    }

    public SystemAssistantState(String roleHolderPackage,
                                String voiceInteractionService,
                                String assistComponent,
                                String label,
                                boolean isAvailable,
                                String source) {
        this.roleHolderPackage = roleHolderPackage;
        this.voiceInteractionService = voiceInteractionService;
        this.assistComponent = assistComponent;
        this.label = label;
        this.isAvailable = isAvailable;
        this.source = source == null || source.isEmpty() ? SOURCE_NONE : source;
    }

    private static String primarySource(String roleHolderPackage, String assistComponent) {
        if (roleHolderPackage != null && !roleHolderPackage.isEmpty()) {
            return SOURCE_ROLE;
        }
        if (assistComponent != null && !assistComponent.isEmpty()) {
            return SOURCE_ASSIST_COMPONENT;
        }
        return SOURCE_VIS;
    }

    public String describe() {
        if (!isAvailable) {
            return "未设置或无法识别";
        }
        String name = label == null || label.isEmpty() ? "未知" : label;
        String pkg = roleHolderPackage != null && !roleHolderPackage.isEmpty()
            ? roleHolderPackage
            : packageOf(assistComponent != null && !assistComponent.isEmpty()
                ? assistComponent
                : voiceInteractionService);
        return pkg == null || pkg.isEmpty() ? name : name + "（" + pkg + "）";
    }

    /** 来源的人类可读说明（首页次级信息）。 */
    public String sourceLabel() {
        if (SOURCE_ROLE.equals(source)) {
            return "来源：ROLE_ASSISTANT";
        }
        if (SOURCE_ASSIST_COMPONENT.equals(source)) {
            return "来源：电源键助手组件";
        }
        if (SOURCE_VIS.equals(source)) {
            return "来源：VoiceInteractionService";
        }
        return "标准 Android 助手状态不可用";
    }

    private static String packageOf(String flattened) {
        if (flattened == null || flattened.isEmpty()) {
            return "";
        }
        int slash = flattened.indexOf('/');
        return slash > 0 ? flattened.substring(0, slash) : flattened;
    }
}
