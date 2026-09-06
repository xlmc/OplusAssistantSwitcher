package com.ouhuan.oplusassistant.shared;

import java.lang.reflect.Method;
import java.util.List;

/**
 * ROLE_ASSISTANT 持有者读取辅助。
 * 不同 API 级别上 RoleManager#getRoleHolders 的签名存在差异，
 * 这里按方法名扫描全部 public 重载做签名兼容，任何失败都返回 null
 * （调用方退化到 VIS / 助手组件交叉验证）。
 * 纯 Java 实现，双端（system_server Hook 侧与 App 侧）均可使用。
 */
public final class RoleHolders {

    /** RoleManager.ROLE_ASSISTANT 的稳定字符串值。 */
    public static final String ROLE_NAME = "android.app.role.ASSISTANT";

    private RoleHolders() {
    }

    /**
     * 返回角色的第一个持有者包名；roleManager 可为 null，失败返回 null。
     *
     * @param roleManager android.app.role.RoleManager 实例
     * @param userHandle  android.os.Process.myUserHandle() 的结果（可为 null）
     */
    public static String primaryHolder(Object roleManager, Object userHandle) {
        if (roleManager == null) {
            return null;
        }
        Object holders = invokeGetRoleHolders(roleManager, userHandle);
        if (holders instanceof List) {
            List<?> list = (List<?>) holders;
            if (!list.isEmpty() && list.get(0) != null) {
                return String.valueOf(list.get(0));
            }
        }
        return null;
    }

    private static Object invokeGetRoleHolders(Object roleManager, Object userHandle) {
        for (Method method : roleManager.getClass().getMethods()) {
            if (!"getRoleHolders".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            try {
                if (params.length == 1 && params[0] == String.class) {
                    return method.invoke(roleManager, ROLE_NAME);
                }
                if (params.length == 2 && params[0] == String.class
                    && params[1].isInstance(userHandle)) {
                    return method.invoke(roleManager, ROLE_NAME, userHandle);
                }
            } catch (Throwable ignored) {
                // 继续尝试下一个重载
            }
        }
        return null;
    }
}
