package com.ouhuan.oplusassistant.ui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
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
import com.ouhuan.oplusassistant.shared.AssistantCandidate;
import com.ouhuan.oplusassistant.system.AssistantScanner;

import java.util.ArrayList;
import java.util.List;

/**
 * 助手选择页（开发书 9；Issue #1 重构）。
 * 普通列表只显示：应用图标、助手名称、简短来源、单选状态。
 * 包名 / ComponentName / 检测细节一律移至诊断页。
 */
public class AssistantPickerActivity extends AppCompatActivity {

    private Adapter adapter;
    private TextView tvEmpty;
    private TextView tvNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant_picker);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvNote = findViewById(R.id.tvNote);
        RecyclerView rv = findViewById(R.id.rvAssistants);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        load();
    }

    private void load() {
        AppExecutors.io().execute(() -> {
            List<AssistantCandidate> candidates =
                new AssistantScanner().scan(AssistantPickerActivity.this);
            String current = ConfigStore.selectedPackage(AssistantPickerActivity.this);
            runOnUiThread(() -> {
                adapter.setItems(candidates, current);
                tvEmpty.setVisibility(candidates.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void select(AssistantCandidate candidate) {
        boolean remoteOk = ConfigStore.writeSelection(this,
            candidate.packageName, candidate.componentName, candidate.eligibilitySource);
        adapter.setSelected(candidate.packageName);
        tvNote.setVisibility(remoteOk ? View.GONE : View.VISIBLE);
    }

    /** 来源信息：安装渠道 / 是否系统预装（不含包名等开发细节）。 */
    private String describeSource(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            boolean systemApp = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    String installer = pm.getInstallSourceInfo(pkg)
                        .getInstallingPackageName();
                    if ("com.android.vending".equals(installer)) {
                        return getString(R.string.picker_source_play);
                    }
                } catch (Throwable ignored) {
                    // 无安装来源记录
                }
            }
            if (systemApp) {
                return getString(R.string.picker_source_system);
            }
        } catch (Throwable ignored) {
        }
        return getString(R.string.picker_source_unknown);
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
            holder.tvSource.setText(describeSource(pm, item.packageName));
            try {
                holder.ivIcon.setImageDrawable(pm.getApplicationIcon(item.packageName));
            } catch (Throwable ignored) {
            }
            holder.rb.setChecked(item.packageName.equals(selectedPackage));
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
            final TextView tvSource;

            Holder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                rb = itemView.findViewById(R.id.rbSelect);
                tvLabel = itemView.findViewById(R.id.tvLabel);
                tvSource = itemView.findViewById(R.id.tvSource);
                rb.setClickable(false);
            }
        }
    }
}
