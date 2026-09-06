package com.ouhuan.oplusassistant.shared;

/**
 * 当前系统默认助手状态（开发书 5.1 SystemAssistantState）。
 * UI 显示必须来自系统实时状态，不允许用模块上次选择冒充。
 */
public final class SystemAssistantState {

    public final String roleHolderPackage;
    public final String voiceInteractionService;
    public final String label;
    public final boolean isAvailable;

    public SystemAssistantState(String roleHolderPackage,
                                String voiceInteractionService,
                                String label,
                                boolean isAvailable) {
        this.roleHolderPackage = roleHolderPackage;
        this.voiceInteractionService = voiceInteractionService;
        this.label = label;
        this.isAvailable = isAvailable;
    }

    public String describe() {
        if (!isAvailable) {
            return "未检测到";
        }
        String name = label == null || label.isEmpty() ? "未知" : label;
        String pkg = roleHolderPackage != null && !roleHolderPackage.isEmpty()
            ? roleHolderPackage
            : (voiceInteractionService == null ? "" : voiceInteractionService);
        return pkg == null || pkg.isEmpty() ? name : name + "（" + pkg + "）";
    }
}
