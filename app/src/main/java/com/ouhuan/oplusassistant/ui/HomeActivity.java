package com.ouhuan.oplusassistant.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.ouhuan.oplusassistant.R;
import com.ouhuan.oplusassistant.app.AssistApp;
import com.ouhuan.oplusassistant.app.ConfigStore;
import com.ouhuan.oplusassistant.data.AppExecutors;
import com.ouhuan.oplusassistant.data.AssistantStateStore;
import com.ouhuan.oplusassistant.data.HookStateStore;
import com.ouhuan.oplusassistant.data.LogDb;
import com.ouhuan.oplusassistant.data.LogEntity;
import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.CurrentAssistantState;
import com.ouhuan.oplusassistant.shared.LaunchResult;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 首页（开发书 9；Issue #1 评论 4 视觉基线 docs/ouhuan-ui-reference-v1.svg）：
 * 标题/副标题 → 模块状态卡 → 当前系统默认助手卡（system_server 解析的
 * CurrentOplusAssistant，小布等 OEM 助手可直接识别）→ 0.5 秒电源键当前助手卡
 * （含渐变主按钮）→ 最近一次调用卡 → 底部查看日志/诊断信息与版本页脚。
 * 包名/组件名等开发信息一律移至诊断页。
 */
public class HomeActivity extends AppCompatActivity {

    private TextView tvModuleStatus;
    private TextView tvHookStatusSecondary;
    private TextView tvScope;
    private TextView tvSystemAssistant;
    private TextView tvSystemAssistantSecondary;
    private TextView tvAssistantBadge;
    private TextView tvPowerTarget;
    private TextView tvPowerTargetSecondary;
    private TextView tvLastCall;
    private TextView tvLastCallSecondary;
    private TextView tvVersion;
    private TextView tvEnglishName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        // P0-3：targetSdk 35 强制 edge-to-edge，全部内容从状态栏下方开始
        SystemBars.applyInsets(findViewById(android.R.id.content));
        tvModuleStatus = findViewById(R.id.tvModuleStatus);
        tvHookStatusSecondary = findViewById(R.id.tvHookStatusSecondary);
        tvScope = findViewById(R.id.tvScope);
        tvSystemAssistant = findViewById(R.id.tvSystemAssistant);
        tvSystemAssistantSecondary = findViewById(R.id.tvSystemAssistantSecondary);
        tvAssistantBadge = findViewById(R.id.tvAssistantBadge);
        tvPowerTarget = findViewById(R.id.tvPowerTarget);
        tvPowerTargetSecondary = findViewById(R.id.tvPowerTargetSecondary);
        tvLastCall = findViewById(R.id.tvLastCall);
        tvLastCallSecondary = findViewById(R.id.tvLastCallSecondary);
        tvVersion = findViewById(R.id.tvVersion);
        tvEnglishName = findViewById(R.id.tvEnglishName);

        tvScope.setText(R.string.home_scope_line);
        try {
            String version = getPackageManager()
                .getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText(getString(R.string.home_version_format, version));
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText(R.string.app_name);
        }
        tvEnglishName.setText(R.string.app_english_name);

        findViewById(R.id.btnPickAssistant).setOnClickListener(v ->
            startActivity(new Intent(this, AssistantPickerActivity.class)));
        findViewById(R.id.btnViewLogs).setOnClickListener(v ->
            startActivity(new Intent(this, LogsActivity.class)));
        findViewById(R.id.btnDiagnostics).setOnClickListener(v ->
            startActivity(new Intent(this, DiagnosticsActivity.class)));
        findViewById(R.id.btnSettings).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        AppExecutors.io().execute(() -> {
            boolean enabled = ConfigStore.isModuleEnabled(this);
            boolean serviceBound = AssistApp.service() != null;
            String selectedPkg = ConfigStore.selectedPackage(this);
            CurrentAssistantState current = AssistantStateStore.current(this);
            String hookStatus = HookStateStore.status(this);
            LogEntity last = LogDb.get(this).dao().last();

            // ---- 模块状态卡：是否生效 + Hook 次级状态 + 作用域 ----
            boolean hookOk = Constants.EV_HOOK_INSTALLED.equals(hookStatus);
            int moduleColor;
            String moduleMain;
            if (!enabled) {
                moduleMain = getString(R.string.home_module_disabled);
                moduleColor = ContextCompat.getColor(this, R.color.ouhuan_red);
            } else if (hookOk) {
                moduleMain = getString(R.string.home_module_active);
                moduleColor = ContextCompat.getColor(this, R.color.ouhuan_green);
            } else if (hookStatus != null) {
                moduleMain = getString(R.string.home_module_enabled_wait_reboot);
                moduleColor = ContextCompat.getColor(this, R.color.ouhuan_text_body);
            } else {
                moduleMain = getString(R.string.home_module_inactive);
                moduleColor = ContextCompat.getColor(this, R.color.ouhuan_red);
            }
            StringBuilder hookSecondary = new StringBuilder();
            if (hookStatus == null) {
                hookSecondary.append(getString(R.string.home_hook_status_format,
                    getString(R.string.home_hook_not_active)));
            } else {
                hookSecondary.append(getString(R.string.home_hook_status_format,
                    describeHookStatus(hookStatus)));
            }
            if (!serviceBound) {
                hookSecondary.append(" · ").append(getString(R.string.home_service_unbound));
            }

            // ---- 当前系统默认助手卡：system_server 解析的 CurrentOplusAssistant ----
            // （小布等 OEM 助手由 system_server 直接识别；ROLE_ASSISTANT / VIS 原始值在诊断页）
            String assistantMain;
            String assistantSecondary;
            boolean badge;
            if (current != null && !current.displayName.isEmpty()) {
                assistantMain = current.displayName;
                assistantSecondary = "";
                badge = true;
            } else if (current != null
                && CurrentAssistantState.SOURCE_NONE.equals(current.source)) {
                assistantMain = getString(R.string.home_assistant_unrecognized);
                assistantSecondary = "";
                badge = false;
            } else {
                assistantMain = getString(R.string.home_assistant_pending);
                assistantSecondary = getString(R.string.home_assistant_pending_hint);
                badge = false;
            }

            // ---- 电源键当前助手卡 ----
            String targetMain;
            String targetSecondary;
            if (!enabled) {
                targetMain = getString(R.string.home_target_not_taken_over);
                targetSecondary = getString(R.string.home_target_secondary_disabled);
            } else if (selectedPkg == null || selectedPkg.trim().isEmpty()) {
                targetMain = getString(R.string.home_target_none_selected);
                targetSecondary = getString(R.string.home_target_pick_hint);
            } else {
                targetMain = describePackageLabel(selectedPkg);
                targetSecondary = getString(R.string.home_target_secondary_taken_over);
            }

            // ---- 最近一次调用卡 ----
            String lastMain;
            String lastSecondary = "";
            if (last == null) {
                lastMain = getString(R.string.home_last_call_none);
            } else {
                String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new Date(last.timestamp));
                if (LaunchResult.SUCCESS.name().equals(last.result)) {
                    lastMain = getString(R.string.home_last_call_success) + " · " + time;
                } else {
                    lastMain = getString(R.string.home_last_call_failure) + " · " + time;
                    lastSecondary = last.failureCode == null ? "" : last.failureCode;
                }
            }

            String fModuleMain = "● " + moduleMain;
            int fModuleColor = moduleColor;
            String fHookSecondary = hookSecondary.toString();
            String fAssistantMain = assistantMain;
            String fAssistantSecondary = assistantSecondary;
            boolean fBadge = badge;
            String fTargetMain = targetMain;
            String fTargetSecondary = targetSecondary;
            String fLastMain = lastMain;
            String fLastSecondary = lastSecondary;
            runOnUiThread(() -> {
                tvModuleStatus.setText(fModuleMain);
                tvModuleStatus.setTextColor(fModuleColor);
                tvHookStatusSecondary.setText(fHookSecondary);
                tvSystemAssistant.setText(fAssistantMain);
                tvAssistantBadge.setVisibility(fBadge ? View.VISIBLE : View.GONE);
                if (fAssistantSecondary.isEmpty()) {
                    tvSystemAssistantSecondary.setVisibility(View.GONE);
                } else {
                    tvSystemAssistantSecondary.setVisibility(View.VISIBLE);
                    tvSystemAssistantSecondary.setText(fAssistantSecondary);
                }
                tvPowerTarget.setText(fTargetMain);
                tvPowerTargetSecondary.setText(fTargetSecondary);
                tvLastCall.setText(fLastMain);
                if (fLastSecondary.isEmpty()) {
                    tvLastCallSecondary.setVisibility(View.GONE);
                } else {
                    tvLastCallSecondary.setVisibility(View.VISIBLE);
                    tvLastCallSecondary.setText(fLastSecondary);
                }
            });
        });
    }

    private String describeHookStatus(String status) {
        switch (status) {
            case Constants.EV_HOOK_INSTALLED:
                return getString(R.string.hook_ok);
            case Constants.EV_HOOK_FAILED:
                return getString(R.string.hook_failed);
            case Constants.EV_HOOK_TARGET_NOT_FOUND:
                return getString(R.string.hook_target_missing) + "（" + status + "）";
            case Constants.EV_ROM_UNSUPPORTED:
                return getString(R.string.hook_rom_unsupported) + "（" + status + "）";
            default:
                return getString(R.string.hook_initializing) + "（" + status + "）";
        }
    }

    /** 首页仅显示应用名，包名/组件名等开发信息移至诊断页。 */
    private String describePackageLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(info));
        } catch (Throwable t) {
            return pkg;
        }
    }
}
