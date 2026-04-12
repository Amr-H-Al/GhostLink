package com.example.ghostlink;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class StatsActivity extends AppCompatActivity {

    private StatsManager statsManager;
    private TextView tvSafeCount, tvRiskyCount, tvDangerCount;
    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private MaterialButton btnClearAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_stats);

        statsManager = new StatsManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvSafeCount = findViewById(R.id.tvSafeCount);
        tvRiskyCount = findViewById(R.id.tvRiskyCount);
        tvDangerCount = findViewById(R.id.tvDangerCount);
        rvHistory = findViewById(R.id.rvHistory);
        btnClearAll = findViewById(R.id.btnClearAll);

        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> showClearAllConfirmation());
        }

        updateStats();
        setupHistoryList();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.stats_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void showClearAllConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Data?")
                .setMessage("This will permanently delete all scan history, screenshots, and reset your statistics. This action cannot be undone.")
                .setPositiveButton("Clear Everything", (dialog, which) -> {
                    statsManager.clearAllData();
                    updateStats();
                    setupHistoryList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateStats() {
        tvSafeCount.setText(String.valueOf(statsManager.getSafeCount()));
        tvRiskyCount.setText(String.valueOf(statsManager.getRiskyCount()));
        tvDangerCount.setText(String.valueOf(statsManager.getDangerCount()));
    }

    private void setupHistoryList() {
        List<HistoryItem> history = statsManager.getHistory();
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(history, item -> showOptionsDialog(item));
        rvHistory.setAdapter(adapter);

        // Hide Clear All button if there's no data to clear
        if (btnClearAll != null) {
            boolean hasData = !history.isEmpty() || statsManager.getSafeCount() > 0
                    || statsManager.getRiskyCount() > 0 || statsManager.getDangerCount() > 0;
            btnClearAll.setVisibility(hasData ? View.VISIBLE : View.GONE);
        }
    }

    private void showOptionsDialog(HistoryItem item) {
        String[] options = (item.getScreenshotPath() != null) 
            ? new String[]{"View Scam Detection Details", "Delete Activity"} 
            : new String[]{"Delete Activity"};

        new AlertDialog.Builder(this)
                .setTitle("Activity Options")
                .setItems(options, (dialog, which) -> {
                    if (options[which].equals("View Scam Detection Details")) {
                        Intent intent = new Intent(this, ScreenshotViewerActivity.class);
                        intent.putExtra(ScreenshotViewerActivity.EXTRA_PATH, item.getScreenshotPath());
                        intent.putExtra(ScreenshotViewerActivity.EXTRA_LABEL, item.getLabel());
                        intent.putExtra(ScreenshotViewerActivity.EXTRA_CONFIDENCE, item.getConfidence());
                        intent.putExtra(ScreenshotViewerActivity.EXTRA_REASON, item.getReason());
                        startActivity(intent);
                    } else if (options[which].equals("Delete Activity")) {
                        statsManager.deleteHistoryItem(item.getId());
                        setupHistoryList(); // Refresh list
                    }
                })
                .show();
    }

    private interface OnItemClickListener {
        void onItemClick(HistoryItem item);
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private final List<HistoryItem> items;
        private final OnItemClickListener listener;

        HistoryAdapter(List<HistoryItem> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoryItem item = items.get(position);
            holder.tvSnippet.setText(item.getSnippet());
            holder.tvReason.setText("Reason: " + item.getReason());
            holder.tvConfidence.setText(item.getConfidence() + "%");
            
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(item.getTimestamp(), 
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            holder.tvTime.setText(timeAgo);

            String label = item.getLabel();
            holder.tvLabel.setText(label.equals("NOT_SAFE") ? "DANGER" : label);
            
            int color;
            if ("SAFE".equals(label)) color = 0xFF1B5E20;
            else if ("RISKY".equals(label)) color = 0xFFE65100;
            else color = 0xFFB71C1C;
            
            holder.tvLabel.setBackgroundColor(color);
            holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvLabel, tvTime, tvConfidence, tvSnippet, tvReason;

            ViewHolder(View itemView) {
                super(itemView);
                tvLabel = itemView.findViewById(R.id.tvHistoryLabel);
                tvTime = itemView.findViewById(R.id.tvHistoryTime);
                tvConfidence = itemView.findViewById(R.id.tvHistoryConfidence);
                tvSnippet = itemView.findViewById(R.id.tvHistorySnippet);
                tvReason = itemView.findViewById(R.id.tvHistoryReason);
            }
        }
    }
}
