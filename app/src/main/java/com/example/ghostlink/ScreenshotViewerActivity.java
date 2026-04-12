package com.example.ghostlink;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.io.File;

public class ScreenshotViewerActivity extends AppCompatActivity {

    public static final String EXTRA_PATH = "extra_screenshot_path";
    public static final String EXTRA_LABEL = "extra_label";
    public static final String EXTRA_CONFIDENCE = "extra_confidence";
    public static final String EXTRA_REASON = "extra_reason";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screenshot_viewer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ImageView ivScreenshot = findViewById(R.id.ivScreenshot);
        TextView tvNoImage = findViewById(R.id.tvNoImage);
        TextView tvRiskValue = findViewById(R.id.tvRiskLevelValue);
        TextView tvExplanation = findViewById(R.id.tvRiskExplanation);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        String label = getIntent().getStringExtra(EXTRA_LABEL);
        int confidence = getIntent().getIntExtra(EXTRA_CONFIDENCE, 0);
        String reason = getIntent().getStringExtra(EXTRA_REASON);

        // Set Screenshot
        if (path != null) {
            File file = new File(path);
            if (file.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(path);
                if (bitmap != null) {
                    ivScreenshot.setImageBitmap(bitmap);
                    tvNoImage.setVisibility(View.GONE);
                } else {
                    tvNoImage.setVisibility(View.VISIBLE);
                }
            } else {
                tvNoImage.setVisibility(View.VISIBLE);
            }
        } else {
            tvNoImage.setVisibility(View.VISIBLE);
        }

        // Set Risk Information
        String displayLabel = (label != null && label.equals("NOT_SAFE")) ? "DANGER" : (label != null ? label : "UNKNOWN");
        tvRiskValue.setText(displayLabel + " (" + confidence + "% Confidence)");
        
        // Dynamic color for risk label
        int color = 0xFFB71C1C; // Default DANGER
        if ("SAFE".equals(label)) color = 0xFF1B5E20;
        else if ("RISKY".equals(label)) color = 0xFFE65100;
        tvRiskValue.setTextColor(color);

        // Generate a 3-sentence explanation based on the reason
        String explanation = generateExplanation(displayLabel, reason);
        tvExplanation.setText(explanation);
    }

    private String generateExplanation(String label, String reason) {
        String sentence1 = "The content on this screen was flagged as " + label.toLowerCase() + " by GhostLink's AI.";
        String sentence2 = "The primary indicator detected was " + (reason != null && !reason.isEmpty() ? reason.toLowerCase() : "suspicious linguistic patterns") + ".";
        String sentence3 = "This specific pattern is commonly used in fraudulent attempts to deceive users into taking immediate action.";
        return sentence1 + " " + sentence2 + " " + sentence3;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
