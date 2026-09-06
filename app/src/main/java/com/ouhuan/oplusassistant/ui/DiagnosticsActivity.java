package com.ouhuan.oplusassistant.ui;

import android.os.Bundle;
import android.content.pm.PackageInfo;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ouhuan.oplusassistant.R;
import com.ouhuan.oplusassistant.app.AssistApp;
import com.ouhuan.oplusassistant.app.ConfigStore;
import com.ouhuan.oplusassistant.data.AppExecutors;
import com.ouhuan.oplusassistant.data.AssistantStateStore;
import com.ouhuan.oplusassistant.data.HookStateStore;
import com.ouhuan.oplusassistant.data.LogDb;
import com.ouhuan.oplusassistant.shared.AssistantCandidate;
import com.ouhuan.oplusassistant.shared.Constants;
import com.ouhuan.oplusassistant.shared.CurrentAssistantState;
import com.ouhuan.oplusassistant.shared.ModuleVersionState;
import com.ouhuan.oplusassistant.shared.SystemAssistantState;
import com.ouhuan.oplusassistant.system.AssistantScanner;
import com.ouhuan.oplusassistant.system.DeviceProps;
import com.ouhuan.oplusassistant.system.SystemAssistantReader;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 诊断信息页（开发书 4.1 Diagnostics / 9；Issue #1 第五条）。
 * 普通页面收敛后，完整技术数据集中在此：设备、LSPosed 服务、Hook 状态与技术细节
 * （strategy / class / method / trigger）、系统默认助手原始检测值、
 * 选择详情（packageName / ComponentName / 资格判定依据）与日志统计。
 */
public class DiagnosticsActivity extends AppCompatActivity {

    private TextView tvDevice;
    private TextView tvService;
    private TextView tvHook;
    private TextView tvHookTech;
    private TextView tvAssistantDetail;
    private TextView tvSystemServer;
    private TextView tvModuleVersion;
    private TextView tvCandidates;
    private TextView tvSelection;
    private TextView tvStats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);
        // P0-3：targetSdk 35 强制 edge-to-edge，全部内容从状态栏下方开始
        SystemBars.applyInsets(findViewById(android.R.id.content));
        tvDevice = findViewById(R.id.tvDevice);
        tvService = findViewById(R.id.tvService);
        tvHook = findViewById(R.id.tvHook);
        tvHookTech = findViewById(R.id.tvHookTech);
        tvAssistantDetail = findViewById(R.id.tvAssistantDetail);
        tvSystemServer = findViewById(R.id.tvSystemServer);
        tvModuleVersion = findViewById(R.id.tvModuleVersion);
        tvCandidates = findViewById(R.id.tvCandidates);
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
            }

            String hookTech = "hookStrategy=" + Constants.HOOK_STRATEGY_COLOROS16
                + "\nhookClass=" + Constants.HOOK_CLASS
                + "\nhookMethod=" + Constants.HOOK_METHOD
                + "\ntrigger=" + Constants.TRIGGER_POWER_ASSIST_0X3F3
                + " (Message.what=0x3F3)"
                + "\nscope=system";

            SystemAssistantState systemDefault = new SystemAssistantReader().read(this);
            String assistantDetail = getString(R.string.diagnostics_std_assistant)
                + "\n" + systemDefault.describe()
                + "\nsource=" + systemDefault.source
                + "\nroleHolder=" + orDash(systemDefault.roleHolderPackage)
                + "\nvoiceInteractionService=" + orDash(systemDefault.voiceInteractionService)
                + "\nassistComponent(ColorOS 电源键原始目标)="
                + orDash(systemDefault.assistComponent);

            // system_server 解析的「当前手机实际助手」（CurrentOplusAssistant，最高可信来源）
            CurrentAssistantState current = AssistantStateStore.current(this);
            String systemServer;
            if (current == null) {
                systemServer = getString(R.string.diagnostics_ss_none);
            } else {
                systemServer = "displayName=" + orDash(current.displayName)
                    + "\npackageName=" + orDash(current.packageName)
                    + "\ncomponentName=" + orDash(current.componentName)
                    + "\nsource=" + current.source
                    + "\nresolvedFromSystemServer=" + current.resolvedFromSystemServer
                    + "\nroleHolder=" + orDash(current.roleHolderPackage)
                    + "\nvoiceInteractionService=" + orDash(current.voiceInteractionService)
                    + "\nupdatedAt=" + (current.timestamp > 0
                        ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date(current.timestamp))
                        : "-");
            }

            AppVersion appVersion = readAppVersion();
            ModuleVersionState serverModule = AssistantStateStore.moduleVersion(this);
            ModuleVersionState.Comparison versionComparison = serverModule == null
                ? ModuleVersionState.Comparison.UNKNOWN
                : serverModule.compareTo(appVersion.name, appVersion.code);
            String moduleVersion = describeModuleVersion(appVersion, serverModule,
                versionComparison);

            // 候选清单与每个候选的资格来源（本地扫描 ∪ system_server 上报，仅诊断页展示）
            StringBuilder candidates = new StringBuilder();
            List<AssistantCandidate> scanned =
                new AssistantScanner().scan(this);
            List<AssistantCandidate> merged = new java.util.ArrayList<>(scanned);
            for (AssistantCandidate remote : AssistantStateStore.candidates(this)) {
                boolean present = false;
                for (AssistantCandidate localCandidate : merged) {
                    if (localCandidate.packageName.equals(remote.packageName)) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    merged.add(remote);
                }
            }
            if (merged.isEmpty()) {
                candidates.append(getString(R.string.picker_empty));
            } else {
                for (AssistantCandidate c : merged) {
                    if (candidates.length() > 0) {
                        candidates.append("\n\n");
                    }
                    candidates.append(c.label)
                        .append("\npkg=").append(c.packageName)
                        .append("\nentry=").append(c.componentName)
                        .append("\nlaunchMethod=").append(c.launchMethod)
                        .append("\neligibility=").append(c.eligibilitySource);
                }
            }

            String selection;
            if (!ConfigStore.isModuleEnabled(this)) {
                selection = getString(R.string.home_module_disabled);
            } else {
                String pkg = ConfigStore.selectedPackage(this);
                selection = pkg == null || pkg.trim().isEmpty()
                    ? getString(R.string.home_target_none_selected)
                    : "packageName=" + pkg
                        + "\nComponentName=" + orDash(ConfigStore.selectedComponent(this))
                        + "\neligibility=" + orDash(ConfigStore.selectedSource(this));
            }

            int total = LogDb.get(this).dao().totalCount();
            int failures = LogDb.get(this).dao().failureCount();
            String stats = getString(R.string.diagnostics_total) + ": " + total
                + "\n" + getString(R.string.diagnostics_failures) + ": " + failures;

            String finalDevice = device;
            String finalService = service;
            String finalHook = hook;
            String finalHookTech = hookTech;
            String finalAssistantDetail = assistantDetail;
            String finalSystemServer = systemServer;
            String finalModuleVersion = moduleVersion;
            String finalCandidates = candidates.toString();
            String finalSelection = selection;
            String finalStats = stats;
            runOnUiThread(() -> {
                tvDevice.setText(finalDevice);
                tvService.setText(finalService);
                tvHook.setText(finalHook);
                tvHookTech.setText(finalHookTech);
                tvAssistantDetail.setText(finalAssistantDetail);
                tvSystemServer.setText(finalSystemServer);
                tvModuleVersion.setText(finalModuleVersion);
                tvCandidates.setText(finalCandidates);
                tvSelection.setText(finalSelection);
                tvStats.setText(finalStats);
            });
        });
    }

    private static String orDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private AppVersion readAppVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            String name = info.versionName == null ? "" : info.versionName;
            return new AppVersion(name, info.getLongVersionCode());
        } catch (Throwable t) {
            return new AppVersion("", ModuleVersionState.UNKNOWN_VERSION_CODE);
        }
    }

    private String describeModuleVersion(AppVersion appVersion,
                                         ModuleVersionState serverModule,
                                         ModuleVersionState.Comparison comparison) {
        String appName = appVersion.name.isEmpty() ? "-" : appVersion.name;
        String appLine = getString(R.string.home_module_version_app, appName,
            String.valueOf(appVersion.code));
        String serverLine;
        if (serverModule == null) {
            serverLine = getString(R.string.home_module_version_server_unknown);
        } else {
            String serverName = serverModule.versionName.isEmpty()
                ? "-" : serverModule.versionName;
            String serverCode = serverModule.versionCode < 0
                ? "-" : String.valueOf(serverModule.versionCode);
            serverLine = getString(R.string.home_module_version_server, serverName, serverCode);
        }
        int statusRes = comparison == ModuleVersionState.Comparison.MATCH
            ? R.string.home_module_version_synced
            : comparison == ModuleVersionState.Comparison.MISMATCH
                ? R.string.home_module_version_reload
                : R.string.home_module_version_pending;
        return appLine + "\n" + serverLine + "\n" + getString(statusRes);
    }

    private static final class AppVersion {
        final String name;
        final long code;

        AppVersion(String name, long code) {
            this.name = name;
            this.code = code;
        }
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
