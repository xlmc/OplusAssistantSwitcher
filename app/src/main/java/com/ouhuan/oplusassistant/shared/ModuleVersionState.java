package com.ouhuan.oplusassistant.shared;

/**
 * App 当前版本与 system_server 已加载模块版本的比较结果（Issue #1 P1）。
 * 该类保持纯 Java，供 system_server Hook 侧与模块 App 侧共用。
 */
public final class ModuleVersionState {

    /** 未能读取 versionCode 时使用的值。 */
    public static final long UNKNOWN_VERSION_CODE = -1L;

    public enum Comparison {
        UNKNOWN,
        MATCH,
        MISMATCH
    }

    public final String versionName;
    public final long versionCode;
    public final long loadedAt;

    public ModuleVersionState(String versionName, long versionCode, long loadedAt) {
        this.versionName = versionName == null ? "" : versionName.trim();
        this.versionCode = versionCode;
        this.loadedAt = loadedAt;
    }

    /** 是否拿到了 system_server 的有效 versionName。 */
    public boolean isKnown() {
        return !versionName.isEmpty() && !"-".equals(versionName);
    }

    /** 与当前 App 版本比较；任一侧不完整时返回 UNKNOWN，不能误报一致。 */
    public Comparison compareTo(String appVersionName, long appVersionCode) {
        if (!isKnown() || appVersionCode < 0 || versionCode < 0
            || appVersionName == null || appVersionName.trim().isEmpty()) {
            return Comparison.UNKNOWN;
        }
        return versionName.equals(appVersionName.trim()) && versionCode == appVersionCode
            ? Comparison.MATCH : Comparison.MISMATCH;
    }
}
