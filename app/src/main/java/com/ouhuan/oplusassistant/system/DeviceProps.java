package com.ouhuan.oplusassistant.system;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * App 进程内的厂商属性读取（诊断页展示用）。
 * 通过 getprop 规避隐藏 API 限制；仅读取系统只读属性。
 */
public final class DeviceProps {

    private DeviceProps() {
    }

    public static String get(String key) {
        try {
            Process process = Runtime.getRuntime()
                .exec(new String[]{"/system/bin/getprop", key});
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line == null ? "" : line.trim();
            } finally {
                process.destroy();
            }
        } catch (Throwable t) {
            return "";
        }
    }

    public static String colorOsVersion() {
        String[] keys = {
            "ro.build.version.oplusrom",
            "ro.oplus.version",
            "ro.vendor.oplus.version"
        };
        for (String key : keys) {
            String value = get(key);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "-";
    }
}
