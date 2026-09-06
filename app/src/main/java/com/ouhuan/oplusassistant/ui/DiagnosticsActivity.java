package com.ouhuan.oplusassistant.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ouhuan.oplusassistant.R;
import com.ouhuan.oplusassistant.app.AssistApp;
import com.ouhuan.oplusassistant.app.ConfigStore;
import com.ouhuan.oplusassistant.data.AppExecutors;
import com.ouhuan.oplusassistant.data.HookStateStore;
import com.ouhuan.oplusassistant.data.LogDb;
import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.system.DeviceProps;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 诊断信息页（开发书 4.1 Diagnostics / 9）：设备信息、LSPosed 服务、
 * Hook 状态详情与日志统计。只读展示。
 */
public class DiagnosticsActivity extends AppCompatActivity {

    private TextView tvDevice;
    private TextView tvService;
    private TextView tvHook;
    private TextView tvSelection;
    private TextView tvStats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);
        tvDevice = findViewById(R.id.tvDevice);
        tvService = findViewById(R.id.tvService);
        tvHook = findViewById(R.id.tvHook);
        tvSelection = findViewById(R.id.tvSelection);
        tvStats = findViewById(R.id.tvStats);
        findViewById(R.id.btnRefresh).setOnClickListener(v -> refresh());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        AppExecutors.io().execute(() -> {
            String device = getString(R.string.diagnostics_android) + ": "
                + android.os.Build.VERSION.RELEASE + " (API "
                + android.os.Build.VERSION.SDK_INT + ")"
                + "\n" + getString(R.string.diagnostics_coloros) + ": "
                + DeviceProps.colorOsVersion()
                + "\n" + getString(R.string.diagnostics_model) + ": "
                + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;

            String service = describeService();

            String hookStatus = HookStateStore.status(this);
            String hook;
            if (hookStatus == null) {
                hook = getString(R.string.home_hook_not_active);
            } else {
                hook = hookStatus;
                String summary = HookStateStore.summary(this);
                if (summary != null && !summary.isEmpty() && !"-".equals(summary)) {
                    hook += "\n" + summary;
                }
                long ts = HookStateStore.timestamp(this);
                if (ts > 0) {
                    hook += "\n" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()).format(new Date(ts));
                }
                String strategy = HookStateStore.strategy(this);
                if (strategy != null && !"-".equals(strategy)) {
                    hook += "\nhookStrategy=" + strategy;
                }
            }

            String selection;
            if (!ConfigStore.isModuleEnabled(this)) {
                selection = getString(R.string.home_module_disabled);
            } else {
                String pkg = ConfigStore.selectedPackage(this);
                selection = pkg == null || pkg.trim().isEmpty()
                    ? getString(R.string.home_target_none_selected)
                    : pkg;
            }

            int total = LogDb.get(this).dao().totalCount();
            int failures = LogDb.get(this).dao().failureCount();
            String stats = getString(R.string.diagnostics_total) + ": " + total
                + "\n" + getString(R.string.diagnostics_failures) + ": " + failures;

            String finalDevice = device;
            String finalService = service;
            String finalHook = hook;
            String finalSelection = selection;
            String finalStats = stats;
            runOnUiThread(() -> {
                tvDevice.setText(finalDevice);
                tvService.setText(finalService);
                tvHook.setText(finalHook);
                tvSelection.setText(finalSelection);
                tvStats.setText(finalStats);
            });
        });
    }

    private String describeService() {
        io.github.libxposed.service.XposedService s = AssistApp.service();
        if (s == null) {
            return getString(R.string.diagnostics_service_unbound);
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(getString(R.string.diagnostics_service_bound));
            sb.append("\nframework=").append(s.getFrameworkName())
                .append(" ").append(s.getFrameworkVersion());
            sb.append("\napi=").append(s.getApiVersion());
            sb.append("\nscope=").append(String.valueOf(s.getScope()));
            return sb.toString();
        } catch (Throwable t) {
            return getString(R.string.diagnostics_service_bound)
                + "\n" + t.getClass().getSimpleName();
        }
    }
}
