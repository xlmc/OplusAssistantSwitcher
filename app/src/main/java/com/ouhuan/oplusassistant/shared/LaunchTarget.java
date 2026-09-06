package com.ouhuan.oplusassistant.shared;

/**
 * AssistantLauncher 解析出的启动目标。
 */
public final class LaunchTarget {

    public final String componentName;
    public final String launchMethod;

    public LaunchTarget(String componentName, String launchMethod) {
        this.componentName = componentName;
        this.launchMethod = launchMethod;
    }
}
