package com.ouhuan.oplusassistant.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
 * 助手选择页（开发书 9）：只显示当前检测到的有效第三方语音助手；
 * 没有有效候选时提示「未检测到可用第三方语音助手」，不展示普通应用列表。
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
            candidate.packageName, candidate.componentName);
        adapter.setSelected(candidate.packageName);
        tvNote.setVisibility(remoteOk ? View.GONE : View.VISIBLE);
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
            holder.tvLabel.setText(item.label);
            holder.tvPackage.setText(item.packageName);
            holder.tvComponent.setText(item.componentName + " · " + item.launchMethod);
            holder.rb.setChecked(item.packageName.equals(selectedPackage));
            holder.itemView.setOnClickListener(v -> select(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final RadioButton rb;
            final TextView tvLabel;
            final TextView tvPackage;
            final TextView tvComponent;

            Holder(@NonNull View itemView) {
                super(itemView);
                rb = itemView.findViewById(R.id.rbSelect);
                tvLabel = itemView.findViewById(R.id.tvLabel);
                tvPackage = itemView.findViewById(R.id.tvPackage);
                tvComponent = itemView.findViewById(R.id.tvComponent);
                rb.setClickable(false);
            }
        }
    }
}
