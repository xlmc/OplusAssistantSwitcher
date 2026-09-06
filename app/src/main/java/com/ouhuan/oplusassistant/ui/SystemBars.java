package com.ouhuan.oplusassistant.ui;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 统一处理系统栏 insets（Issue #1 P0-3）。
 * targetSdk 35 强制 edge-to-edge，内容默认绘制进状态栏/导航栏，
 * 所有页面根视图都必须应用该 padding，确保任何内容都在
 * 状态栏与导航栏下方开始。
 */
public final class SystemBars {

    private SystemBars() {
    }

    /** 给页面根视图应用 systemBars + displayCutout 的 padding。 */
    public static void applyInsets(View root) {
        if (root == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
