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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
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
            boolean remoteOk = ConfigStore.writeEnabled(this, isChecked);
            updateServiceNote(remoteOk);
        });
        swDetail.setOnCheckedChangeListener((buttonView, isChecked) -> {
            boolean remoteOk = ConfigStore.writeDetail(this, isChecked);
            updateServiceNote(remoteOk);
        });

        findViewById(R.id.btnResetSelection).setOnClickListener(v ->
            AppExecutors.io().execute(() -> ConfigStore.clearSelection(this)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppExecutors.io().execute(() -> {
            boolean enabled = ConfigStore.isModuleEnabled(this);
            boolean detail = ConfigStore.isDetailDiagnostics(this);
            runOnUiThread(() -> {
                swEnabled.setChecked(enabled);
                swDetail.setChecked(detail);
                updateServiceNote(com.ouhuan.oplusassistant.app.AssistApp.service() != null);
            });
        });
    }

    private void updateServiceNote(boolean remoteOk) {
        if (remoteOk) {
            tvServiceState.setText(R.string.settings_service_ok);
        } else {
            tvServiceState.setText(R.string.settings_service_unbound);
        }
    }
}
