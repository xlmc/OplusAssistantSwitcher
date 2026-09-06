package com.ouhuan.oplusassistant.ui;

import android.content.Intent;
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

/**
 * 首页（开发书 9）：模块状态、Hook 状态、系统默认助手、电源键当前助手、
 * 最近一次调用与日志/诊断入口。所有状态读取均走后台线程，回主线程刷新。
 */
public class HomeActivity extends AppCompatActivity {

    private TextView tvModuleStatus;
    private TextView tvHookStatus;
    private TextView tvSystemAssistant;
    private TextView tvPowerTarget;
    private TextView tvLastCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        tvModuleStatus = findViewById(R.id.tvModuleStatus);
        tvHookStatus = findViewById(R.id.tvHookStatus);
        tvSystemAssistant = findViewById(R.id.tvSystemAssistant);
        tvPowerTarget = findViewById(R.id.tvPowerTarget);
        tvLastCall = findViewById(R.id.tvLastCall);

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
            String selectedComp = ConfigStore.selectedComponent(this);
            SystemAssistantState systemDefault = new SystemAssistantReader().read(this);
            String hookStatus = HookStateStore.status(this);
            long hookTimestamp = HookStateStore.timestamp(this);
            LogEntity last = LogDb.get(this).dao().last();

            String moduleLine = enabled
                ? getString(R.string.home_module_enabled)
                : getString(R.string.home_module_disabled);
            if (!serviceBound) {
                moduleLine += "\n" + getString(R.string.home_service_unbound);
            }

            String hookLine;
            if (hookStatus == null) {
                hookLine = getString(R.string.home_hook_not_active);
            } else {
                String meaning = describeHookStatus(hookStatus);
                hookLine = meaning + "（" + hookStatus + "）";
                if (hookTimestamp > 0) {
                    hookLine += "\n" + android.text.format.DateUtils.formatDateTime(this,
                        hookTimestamp,
                        android.text.format.DateUtils.FORMAT_SHOW_TIME
                            | android.text.format.DateUtils.FORMAT_SHOW_DATE);
                }
            }

            String targetLine;
            if (!enabled) {
                targetLine = getString(R.string.home_target_not_taken_over);
            } else if (selectedPkg == null || selectedPkg.trim().isEmpty()) {
                targetLine = getString(R.string.home_target_none_selected);
            } else {
                targetLine = describePackage(selectedPkg, selectedComp);
            }

            String lastCallLine = last == null
                ? getString(R.string.home_last_call_none)
                : (LaunchResult.SUCCESS.name().equals(last.result)
                    ? getString(R.string.home_last_call_success) + " · "
                    + last.toModel().formatTimestamp() + " · " + last.event
                    : getString(R.string.home_last_call_failure) + " · "
                    + last.toModel().formatTimestamp() + " · "
                    + last.failureCode);

            String finalModuleLine = moduleLine;
            String finalHookLine = hookLine;
            String finalTargetLine = targetLine;
            String finalLastCallLine = lastCallLine;
            runOnUiThread(() -> {
                tvModuleStatus.setText(finalModuleLine);
                tvHookStatus.setText(finalHookLine);
                tvSystemAssistant.setText(systemDefault.describe());
                tvPowerTarget.setText(finalTargetLine);
                tvLastCall.setText(finalLastCallLine);
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
                return getString(R.string.hook_target_missing);
            case Constants.EV_ROM_UNSUPPORTED:
                return getString(R.string.hook_rom_unsupported);
            default:
                return getString(R.string.hook_initializing);
        }
    }

    private String describePackage(String pkg, String component) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            String label = String.valueOf(pm.getApplicationLabel(info));
            return label + "（" + pkg + "）"
                + (component == null || component.isEmpty() ? "" : "\n" + component);
        } catch (Throwable t) {
            return pkg;
        }
    }
}
