package com.ouhuan.oplusassistant.ui;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ouhuan.oplusassistant.R;
import com.ouhuan.oplusassistant.app.ConfigStore;
import com.ouhuan.oplusassistant.data.AppExecutors;
import com.ouhuan.oplusassistant.data.AssistantStateStore;
import com.ouhuan.oplusassistant.shared.AssistantCandidate;
import com.ouhuan.oplusassistant.system.AssistantScanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 助手选择页（开发书 9；Issue #1 评论 4 视觉基线）。
 * 列表项只显示：应用图标 + 助手名称 + 单选状态（不显示任何来源/包名/组件）。
 * 候选 = 本地扫描 ∪ system_server 上报候选（按包名去重，本地优先）。
 * 点选仅标记，底部「确定」写入配置。
 */
public class AssistantPickerActivity extends AppCompatActivity {

    private Adapter adapter;
    private TextView tvEmpty;
    private TextView tvNote;
    private TextView btnConfirm;
    private AssistantCandidate pending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant_picker);
        // P0-3：targetSdk 35 强制 edge-to-edge，全部内容必须从状态栏下方开始
        SystemBars.applyInsets(findViewById(android.R.id.content));
        tvEmpty = findViewById(R.id.tvEmpty);
        tvNote = findViewById(R.id.tvNote);
        btnConfirm = findViewById(R.id.btnConfirm);
        RecyclerView rv = findViewById(R.id.rvAssistants);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        com.google.android.material.appbar.MaterialToolbar toolbar =
            findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> confirm());
        btnConfirm.setEnabled(false);
        btnConfirm.setAlpha(0.5f);
        load();
    }

    private void load() {
        AppExecutors.io().execute(() -> {
            List<AssistantCandidate> merged;
            boolean scanSucceeded = true;
            try {
                List<AssistantCandidate> local =
                    new AssistantScanner().scan(AssistantPickerActivity.this);
                List<AssistantCandidate> fromSystemServer =
                    AssistantStateStore.candidates(AssistantPickerActivity.this);
                merged = merge(local, fromSystemServer);
            } catch (Throwable t) {
                scanSucceeded = false;
                merged = Collections.emptyList();
                com.ouhuan.oplusassistant.data.RuntimeDebugStore.append(this, "app",
                    com.ouhuan.oplusassistant.shared.Constants.EV_RESOLVER_QUERY_FAILED,
                    "assistant_picker_scan", "candidate scan failed", t);
            }
            String current = ConfigStore.selectedPackage(AssistantPickerActivity.this);
            boolean finalScanSucceeded = scanSucceeded;
            List<AssistantCandidate> finalMerged = merged;
            runOnUiThread(() -> {
                adapter.setItems(finalMerged, current);
                pending = findCandidate(current);
                if (pending != null) {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setAlpha(1f);
                }
                if (finalMerged.isEmpty()) {
                    // 空状态必须可见，不能整页留白（Issue #1 P0-3）
                    tvEmpty.setText(finalScanSucceeded
                        ? R.string.picker_empty : R.string.picker_scan_failed);
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                }
            });
        });
    }

    /** 本地扫描 ∪ system_server 候选，按包名去重；system_server 是权威来源。 */
    private List<AssistantCandidate> merge(List<AssistantCandidate> local,
                                           List<AssistantCandidate> fromSystemServer) {
        Map<String, AssistantCandidate> byPackage = new LinkedHashMap<>();
        for (AssistantCandidate candidate : local) {
            byPackage.put(candidate.packageName, candidate);
        }
        for (AssistantCandidate candidate : fromSystemServer) {
            // system_server 具备完整包可见性，且组件 label/入口是最终判定结果；
            // 它必须覆盖 App 侧可能受 package visibility 影响的同包旧值。
            byPackage.put(candidate.packageName, candidate);
        }
        ArrayList<AssistantCandidate> result = new ArrayList<>(byPackage.values());
        Collections.sort(result, (left, right) -> {
            String leftLabel = left.label == null ? "" : left.label;
            String rightLabel = right.label == null ? "" : right.label;
            return leftLabel.compareToIgnoreCase(rightLabel);
        });
        return result;
    }

    private AssistantCandidate findCandidate(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return null;
        }
        for (AssistantCandidate candidate : adapter.items) {
            if (pkg.equals(candidate.packageName)) {
                return candidate;
            }
        }
        return null;
    }

    private void select(AssistantCandidate candidate) {
        pending = candidate;
        adapter.setSelected(candidate.packageName);
        tvNote.setVisibility(View.GONE);
        btnConfirm.setEnabled(true);
        btnConfirm.setAlpha(1f);
    }

    private void confirm() {
        if (pending == null) {
            return;
        }
        AssistantCandidate selected = pending;
        btnConfirm.setEnabled(false);
        AppExecutors.io().execute(() -> {
            boolean remoteOk = ConfigStore.writeSelection(this,
                selected.packageName, selected.componentName, selected.eligibilitySource);
            runOnUiThread(() -> {
                if (remoteOk) {
                    finish();
                } else {
                    tvNote.setVisibility(View.VISIBLE);
                    btnConfirm.setEnabled(true);
                }
            });
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        private final List<AssistantCandidate> items = new ArrayList<>();
        private String selectedPackage;

        void setItems(List<AssistantCandidate> newItems, String currentPackage) {
            items.clear();
            items.addAll(newItems);
            selectedPackage = currentPackage;
            notifyDataSetChanged();
        }

        void setSelected(String pkg) {
            selectedPackage = pkg;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_assistant, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            AssistantCandidate item = items.get(position);
            PackageManager pm = holder.itemView.getContext().getPackageManager();
            holder.tvLabel.setText(item.label);
            holder.ivIcon.setImageResource(R.drawable.ic_card_assistant);
            try {
                holder.ivIcon.setImageDrawable(pm.getApplicationIcon(item.packageName));
            } catch (Throwable ignored) {
                // system_server 候选可能因 App 侧 package visibility 无法加载图标；
                // 保留稳定的助手图标占位，不让正式列表出现空白块。
            }
            holder.rb.setChecked(item.packageName != null
                && item.packageName.equals(selectedPackage));
            holder.itemView.setOnClickListener(v -> select(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView ivIcon;
            final RadioButton rb;
            final TextView tvLabel;

            Holder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                rb = itemView.findViewById(R.id.rbSelect);
                tvLabel = itemView.findViewById(R.id.tvLabel);
                rb.setClickable(false);
            }
        }
    }
}
