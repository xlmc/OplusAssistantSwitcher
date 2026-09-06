package com.ouhuan.oplusassistant.app;

import android.app.Application;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 模块 App：注册 XposedService 监听，获取与 LSPosed 管理器通信的 Binder，
 * 用于写入 Remote Preferences（开发书 4.1 RuntimeConfig 的 App 侧配合）。
 */
public class AssistApp extends Application implements XposedServiceHelper.OnServiceListener {

    private static volatile XposedService service;

    public static XposedService service() {
        return service;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            XposedServiceHelper.registerListener(this);
        } catch (Throwable ignored) {
            // LSPosed 未安装或版本过旧时保持未连接状态
        }
    }

    @Override
    public void onServiceBind(XposedService bound) {
        service = bound;
    }

    @Override
    public void onServiceDied(XposedService died) {
        if (died == service) {
            service = null;
        }
    }
}
