package com.ouhuan.oplusassistant.xposed;

import com.ouhuan.oplusassistant.BuildConfig;

/**
 * 编译进 Xposed dex 的版本常量（Issue #1 P0）。
 * 不能在 system_server 运行时通过 PackageManager 查询，否则读到的是当前安装包而
 * 不是内存中已经加载的那一代代码。BuildConfig 常量会随本次编译直接内联进 dex。
 */
public final class ModuleBuildInfo {

    public static final String VERSION_NAME = BuildConfig.VERSION_NAME;
    public static final long VERSION_CODE = BuildConfig.VERSION_CODE;

    private ModuleBuildInfo() {
    }
}
