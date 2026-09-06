package com.ouhuan.oplusassistant.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.ouhuan.oplusassistant.R;
import com.ouhuan.oplusassistant.data.AppExecutors;
import com.ouhuan.oplusassistant.data.LogDb;
import com.ouhuan.oplusassistant.data.LogEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志页（开发书 9）：查看、按失败筛选、复制单条、复制完整诊断、清空日志。
 */
public class LogsActivity extends AppCompatActivity {

    private Adapter adapter;
    private SwitchMaterial swFailures;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);
        SystemBars.applyInsets(findViewById(android.R.id.content));
        swFailures = findViewById(R.id.swFailures);
        RecyclerView rv = findViewById(R.id.rvLogs);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);

        swFailures.setOnCheckedChangeListener((buttonView, isChecked) -> reload());
        findViewById(R.id.btnCopyAll).setOnClickListener(v -> copyAll());
        findViewById(R.id.btnClear).setOnClickListener(v -> confirmClear());
        reload();
    }

    private void reload() {
        boolean failuresOnly = swFailures.isChecked();
        AppExecutors.io().execute(() -> {
            List<LogEntity> items = failuresOnly
                ? LogDb.get(LogsActivity.this).dao().allFailures()
                : LogDb.get(LogsActivity.this).dao().all();
            runOnUiThread(() -> adapter.setItems(items));
        });
    }

    private void copyAll() {
        AppExecutors.io().execute(() -> {
            List<LogEntity> items = swFailures.isChecked()
                ? LogDb.get(LogsActivity.this).dao().allFailures()
                : LogDb.get(LogsActivity.this).dao().all();
            StringBuilder sb = new StringBuilder();
            sb.append("欧唤 Oplus Assistant Switcher · 完整诊断（").append(items.size()).append(" 条）");
            for (LogEntity item : items) {
                sb.append("\n\n").append(item.toModel().toReadableText());
            }
            String text = sb.toString();
            runOnUiThread(() -> {
                copy(text);
                Toast.makeText(LogsActivity.this, R.string.toast_copied, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.logs_clear_title)
            .setMessage(R.string.logs_clear_message)
            .setPositiveButton(R.string.logs_clear_confirm, (dialog, which) ->
                AppExecutors.io().execute(() -> {
                    LogDb.get(LogsActivity.this).dao().clear();
                    runOnUiThread(this::reload);
                }))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("ouhuan-log", text));
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        private final List<LogEntity> items = new ArrayList<>();

        void setItems(List<LogEntity> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            LogEntity item = items.get(position);
            String kind = "hook".equals(item.kind) ? "HOOK" : "CALL";
            holder.tvTitle.setText("[" + kind + "] " + item.event
                + " · " + item.toModel().formatTimestamp()
                + (item.isFailure ? " · " + item.failureCode : ""));
            StringBuilder detail = new StringBuilder();
            append(detail, "device", item.deviceModel);
            append(detail, "system", item.systemDefaultAssistant);
            append(detail, "target", item.targetPackage);
            append(detail, "component", item.targetComponent);
            append(detail, "launchMethod", item.launchMethod);
            append(detail, "result", item.result);
            append(detail, "failureCode", item.failureCode);
            append(detail, "summary", item.exceptionSummary);
            holder.tvDetail.setText(detail.length() == 0
                ? getString(R.string.logs_no_detail) : detail.toString());
            holder.itemView.setOnClickListener(v -> {
                copy(item.toModel().toReadableText());
                Toast.makeText(LogsActivity.this, R.string.toast_copied,
                    Toast.LENGTH_SHORT).show();
            });
        }

        private void append(StringBuilder sb, String name, String value) {
            if (value != null && !value.isEmpty() && !"-".equals(value)) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(name).append("=").append(value);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView tvTitle;
            final TextView tvDetail;

            Holder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvLogTitle);
                tvDetail = itemView.findViewById(R.id.tvLogDetail);
            }
        }
    }
}
