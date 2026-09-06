package com.ouhuan.oplusassistant.shared;

/**
 * 系统助手状态（开发书 5.1；Issue #1 P0-1 三概念拆分）。
 *
 * 本类只承载「标准 Android 系统默认助手」：ROLE_ASSISTANT 持有者或
 * VoiceInteractionService 配置。ColorOS 电源键助手组件（Settings.Secure
 * assistant）是厂商电源键配置，不属于标准系统默认助手，仅在
 * {@link #assistComponent} 保留原始值供首页次级信息与诊断页展示，
 * 绝不参与 isAvailable / describe() 的判定。
 */
public final class SystemAssistantState {

    /** 检测来源：ROLE_ASSISTANT 持有者（标准）。 */
    public static final String SOURCE_ROLE = "ROLE_ASSISTANT";
    /** 检测来源：VoiceInteractionService 配置（标准）。 */
    public static final String SOURCE_VIS = "VOICE_INTERACTION_SERVICE";
    /** 检测来源：标准状态不可用。 */
    public static final String SOURCE_NONE = "NONE";

    public final String roleHolderPackage;
    public final String voiceInteractionService;
    /** ColorOS 电源键助手组件原始值（Settings.Secure assistant），非标准助手角色。 */
    public final String assistComponent;
    public final String label;
    public final boolean isAvailable;
    public final String source;

    public SystemAssistantState(String roleHolderPackage,
                                String voiceInteractionService,
                                String assistComponent,
                                String label,
                                boolean isAvailable) {
        this(roleHolderPackage, voiceInteractionService, assistComponent, label,
            isAvailable, defaultSource(roleHolderPackage, voiceInteractionService, isAvailable));
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

    private static String defaultSource(String roleHolderPackage,
                                        String voiceInteractionService,
                                        boolean isAvailable) {
        if (!isAvailable) {
            return SOURCE_NONE;
        }
        if (roleHolderPackage != null && !roleHolderPackage.isEmpty()) {
            return SOURCE_ROLE;
        }
        return SOURCE_VIS;
    }

    /** 仅描述标准 Android 系统默认助手。 */
    public String describe() {
        if (!isAvailable) {
            return "未设置或无法识别";
        }
        String name = label == null || label.isEmpty() ? "未知" : label;
        String pkg = roleHolderPackage != null && !roleHolderPackage.isEmpty()
            ? roleHolderPackage
            : packageOf(voiceInteractionService);
        return pkg == null || pkg.isEmpty() ? name : name + "（" + pkg + "）";
    }

    /** 来源的人类可读说明（首页次级信息）。 */
    public String sourceLabel() {
        if (SOURCE_ROLE.equals(source)) {
            return "来源：ROLE_ASSISTANT";
        }
        if (SOURCE_VIS.equals(source)) {
            return "来源：VoiceInteractionService";
        }
        return "未设置标准系统助手";
    }

    /** ColorOS 电源键助手组件的包名（无则空串）。 */
    public String powerKeyPackage() {
        return packageOf(assistComponent);
    }

    private static String packageOf(String flattened) {
        if (flattened == null || flattened.isEmpty()) {
            return "";
        }
        int slash = flattened.indexOf('/');
        return slash > 0 ? flattened.substring(0, slash) : flattened;
    }
}
