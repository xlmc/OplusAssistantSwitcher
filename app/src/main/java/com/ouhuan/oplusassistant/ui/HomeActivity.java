package com.ouhuan.oplusassistant.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ouhuan.oplusassistant.R;
import com.ouhuan.oplusassistant.app.AssistApp;
import com.ouhuan.oplusassistant.app.ConfigStore;
import com.ouhuan.oplusassistant.data.AppExecutors;
import com.ouhuan.oplusassistant.data.HookStateStore;
import com.ouhuan.oplusassistant.data.LogDb;
import com.ouhuan.oplusassistant.data.LogEntity;
import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.LaunchResult;
import com.ouhuan.oplusassistant.shared.SystemAssistantState;
import com.ouhuan.oplusassistant.system.SystemAssistantReader;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 首页（开发书 9；Issue #1 新信息层级）：
 * 模块状态 → 当前系统默认助手 → 0.5 秒电源键当前助手 → 最近一次调用 →
 * 底部操作与版本页脚。包名/组件名等开发信息一律移至诊断页。
 */
public class HomeActivity extends AppCompatActivity {

    private TextView tvModuleStatus;
    private TextView tvHookStatusSecondary;
    private TextView tvScope;
    private TextView tvSystemAssistant;
    private TextView tvSystemAssistantSecondary;
    private TextView tvPowerKeyOriginal;
    private TextView tvPowerTarget;
    private TextView tvPowerTargetSecondary;
    private TextView tvLastCall;
    private TextView tvLastCallSecondary;
    private TextView tvVersion;

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
        tvPowerKeyOriginal = findViewById(R.id.tvPowerKeyOriginal);
        tvPowerTarget = findViewById(R.id.tvPowerTarget);
        tvPowerTargetSecondary = findViewById(R.id.tvPowerTargetSecondary);
        tvLastCall = findViewById(R.id.tvLastCall);
        tvLastCallSecondary = findViewById(R.id.tvLastCallSecondary);
        tvVersion = findViewById(R.id.tvVersion);

        tvScope.setText(getString(R.string.home_scope_format,
            getString(R.string.home_scope_value)));
        try {
            String version = getPackageManager()
                .getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText(getString(R.string.home_version_format, version));
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText(R.string.app_full_name);
        }

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
            SystemAssistantState systemDefault = new SystemAssistantReader().read(this);
            String hookStatus = HookStateStore.status(this);
            LogEntity last = LogDb.get(this).dao().last();

            // ---- 模块状态卡：是否生效 + Hook 次级状态 + 作用域 ----
            boolean hookOk = Constants.EV_HOOK_INSTALLED.equals(hookStatus);
            String moduleMain;
            if (!enabled) {
                moduleMain = getString(R.string.home_module_disabled);
            } else if (hookOk) {
                moduleMain = getString(R.string.home_module_active);
            } else if (hookStatus != null) {
                moduleMain = getString(R.string.home_module_enabled_wait_reboot);
            } else {
                moduleMain = getString(R.string.home_module_inactive);
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

            // ---- 系统默认助手卡：仅标准 Android 状态（Issue #1 P0-1） ----
            // 与 ColorOS 电源键原始目标、Hook 状态三者互不冒充。
            String assistantMain = systemDefault.describe();
            String assistantSecondary = systemDefault.isAvailable
                ? describeSource(systemDefault.source)
                : getString(R.string.home_assistant_source_none);

            // ColorOS 电源键原始目标：独立概念，仅作次级信息展示
            String powerKeyLine = "";
            String powerKeyPkg = systemDefault.powerKeyPackage();
            if (!powerKeyPkg.isEmpty()
                && !powerKeyPkg.equals(systemDefault.roleHolderPackage)) {
                String label = describePackageLabel(powerKeyPkg);
                powerKeyLine = powerKeyPkg.equals(label)
                    ? getString(R.string.home_power_key_original_raw)
                    : getString(R.string.home_power_key_original_format, label);
            }

            // ---- 电源键当前助手卡 ----
            String targetMain;
            String targetSecondary;
            if (!enabled) {
                targetMain = getString(R.string.home_target_not_taken_over);
                targetSecondary = getString(R.string.home_target_secondary_disabled);
            } else if (selectedPkg == null || selectedPkg.trim().isEmpty()) {
                targetMain = getString(R.string.home_target_none_selected);
                targetSecondary = getString(R.string.home_target_secondary_none);
            } else {
                targetMain = describePackageLabel(selectedPkg);
                targetSecondary = "";
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

            String fModuleMain = moduleMain;
            String fHookSecondary = hookSecondary.toString();
            String fAssistantSecondary = assistantSecondary;
            String fPowerKeyLine = powerKeyLine;
            String fTargetMain = targetMain;
            String fTargetSecondary = targetSecondary;
            String fLastMain = lastMain;
            String fLastSecondary = lastSecondary;
            runOnUiThread(() -> {
                tvModuleStatus.setText(fModuleMain);
                tvHookStatusSecondary.setText(fHookSecondary);
                tvSystemAssistant.setText(assistantMain);
                tvSystemAssistantSecondary.setText(fAssistantSecondary);
                if (fPowerKeyLine.isEmpty()) {
                    tvPowerKeyOriginal.setVisibility(android.view.View.GONE);
                } else {
                    tvPowerKeyOriginal.setVisibility(android.view.View.VISIBLE);
                    tvPowerKeyOriginal.setText(fPowerKeyLine);
                }
                tvPowerTarget.setText(fTargetMain);
                tvPowerTargetSecondary.setText(fTargetSecondary);
                tvLastCall.setText(fLastMain);
                if (fLastSecondary.isEmpty()) {
                    tvLastCallSecondary.setVisibility(android.view.View.GONE);
                } else {
                    tvLastCallSecondary.setVisibility(android.view.View.VISIBLE);
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

    private String describeSource(String source) {
        if (SystemAssistantState.SOURCE_ROLE.equals(source)) {
            return getString(R.string.home_assistant_source_role);
        }
        if (SystemAssistantState.SOURCE_VIS.equals(source)) {
            return getString(R.string.home_assistant_source_vis);
        }
        return getString(R.string.home_assistant_source_none);
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
