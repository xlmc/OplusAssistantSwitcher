package com.ouhuan.oplusassistant.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.ouhuan.oplusassistant.R;
import com.ouhuan.oplusassistant.app.ConfigStore;
import com.ouhuan.oplusassistant.data.AppExecutors;

/**
 * 设置页（开发书 4.1 Settings）：模块启用开关、详细诊断模式、关于。
 * 开关写入 Remote Preferences，Hook 侧于下次触发时同步。
 */
public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial swEnabled;
    private SwitchMaterial swDetail;
    private TextView tvServiceState;
    private boolean updating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        SystemBars.applyInsets(findViewById(android.R.id.content));
        swEnabled = findViewById(R.id.swEnabled);
        swDetail = findViewById(R.id.swDetail);
        tvServiceState = findViewById(R.id.tvServiceState);

        TextView tvVersion = findViewById(R.id.tvVersion);
        try {
            String version = getPackageManager()
                .getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText(getString(R.string.settings_version, version));
        } catch (Throwable t) {
            tvVersion.setText("");
        }

        swEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updating) {
                return;
            }
            AppExecutors.io().execute(() -> {
                ConfigStore.writeEnabled(this, isChecked);
                ConfigStore.Status status = ConfigStore.status(this);
                runOnUiThread(() -> updateFromStatus(status));
            });
        });
        swDetail.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updating) {
                return;
            }
            AppExecutors.io().execute(() -> {
                ConfigStore.writeDetail(this, isChecked);
                ConfigStore.Status status = ConfigStore.status(this);
                runOnUiThread(() -> updateFromStatus(status));
            });
        });

        findViewById(R.id.btnResetSelection).setOnClickListener(v ->
            AppExecutors.io().execute(() -> {
                ConfigStore.clearSelection(this);
                ConfigStore.Status status = ConfigStore.status(this);
                runOnUiThread(() -> updateFromStatus(status));
            }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppExecutors.io().execute(() -> {
            ConfigStore.Status status = ConfigStore.status(this);
            runOnUiThread(() -> {
                updateFromStatus(status);
            });
        });
    }

    private void updateFromStatus(ConfigStore.Status status) {
        updating = true;
        swEnabled.setChecked(status.localDesiredEnabled);
        swDetail.setChecked(status.localDesiredDetailDiagnostics);
        updating = false;
        if (status.syncPending) {
            tvServiceState.setText(R.string.settings_sync_pending);
        } else if (status.serviceBound && status.remoteAvailable) {
            tvServiceState.setText(R.string.settings_service_ok);
        } else {
            tvServiceState.setText(R.string.settings_service_unbound);
        }
    }
}
