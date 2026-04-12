package com.example.ghostlink;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GhostLink";

    private ActivityResultLauncher<Intent> screenCaptureLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private MediaProjectionManager mpm;

    private TextView txtStatus;
    private TextView tvRiskLabel;
    private TextView tvRiskReason;
    private TextView tvRiskScore;
    private MaterialCardView riskCard;
    private Button   btnStart;
    private Button   btnStop;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!CaptureServiceV2.ACTION_STATUS.equals(intent.getAction())) return;
            boolean scanning = intent.getBooleanExtra(CaptureServiceV2.EXTRA_IS_SCANNING, false);
            applyButtonState(scanning);
            if (!scanning) resetRiskCard();
        }
    };

    private final BroadcastReceiver riskReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!CaptureServiceV2.ACTION_RISK.equals(intent.getAction())) return;
            String labelStr = intent.getStringExtra(CaptureServiceV2.EXTRA_RISK_LABEL);
            int    score    = intent.getIntExtra(CaptureServiceV2.EXTRA_RISK_SCORE, 0);
            int    conf     = intent.getIntExtra(CaptureServiceV2.EXTRA_RISK_CONFIDENCE, 0);
            String reason   = intent.getStringExtra(CaptureServiceV2.EXTRA_RISK_REASON);
            RiskLabel label;
            try { label = RiskLabel.valueOf(labelStr != null ? labelStr : "MORE_INFO_NEEDED"); }
            catch (Exception e) { label = RiskLabel.MORE_INFO_NEEDED; }
            updateRiskCard(label, score, conf, reason);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> Log.d(TAG, "POST_NOTIFICATIONS: " + granted));
        ensureNotificationPermission();

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Settings.canDrawOverlays(this)) {
                        Toast.makeText(this, "✅ Overlay enabled", Toast.LENGTH_SHORT).show();
                        Button b = findViewById(R.id.btnOverlayPermission);
                        if (b != null) b.setVisibility(View.GONE);
                    }
                });

        txtStatus    = findViewById(R.id.txtStatus);
        tvRiskLabel  = findViewById(R.id.tvRiskLabel);
        tvRiskReason = findViewById(R.id.tvRiskReason);
        tvRiskScore  = findViewById(R.id.tvRiskScore);
        riskCard     = findViewById(R.id.riskCard);
        btnStart     = findViewById(R.id.btnStart);
        btnStop      = findViewById(R.id.btnStop);

        applyButtonState(false);
        resetRiskCard();

        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        screenCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        startCaptureService(result.getResultCode(), result.getData());
                    } else {
                        Toast.makeText(this, "Screen capture permission is required.", Toast.LENGTH_SHORT).show();
                        applyButtonState(false);
                    }
                });

        if (btnStart != null) btnStart.setOnClickListener(v -> onStartClicked());
        if (btnStop  != null) btnStop.setOnClickListener(v  -> onStopClicked());

        Button btnSamples = findViewById(R.id.btnSamples);
        if (btnSamples != null)
            btnSamples.setOnClickListener(v -> startActivity(new Intent(this, SampleScamActivity.class)));

        Button btnNavStats = findViewById(R.id.btnNavStats);
        if (btnNavStats != null)
            btnNavStats.setOnClickListener(v -> {
                // Stop capturing before navigating to Stats
                onStopClicked();
                startActivity(new Intent(this, StatsActivity.class));
            });

        Button btnOverlayPerm = findViewById(R.id.btnOverlayPermission);
        if (btnOverlayPerm != null) {
            btnOverlayPerm.setOnClickListener(v ->
                    overlayPermissionLauncher.launch(new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()))));
            if (Settings.canDrawOverlays(this)) btnOverlayPerm.setVisibility(View.GONE);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void onStartClicked() {
        if (mpm == null) return;
        applyButtonState(true);
        screenCaptureLauncher.launch(mpm.createScreenCaptureIntent());
    }

    private void startCaptureService(int resultCode, Intent resultData) {
        Intent svc = new Intent(this, CaptureServiceV2.class);
        svc.setAction(CaptureServiceV2.ACTION_START);
        svc.putExtra(CaptureServiceV2.EXTRA_RESULT_CODE, resultCode);
        svc.putExtra(CaptureServiceV2.EXTRA_RESULT_DATA, resultData);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
    }

    private void onStopClicked() {
        Intent i = new Intent(this, CaptureServiceV2.class);
        i.setAction(CaptureServiceV2.ACTION_STOP);
        startService(i);
        applyButtonState(false);
        resetRiskCard();
    }

    private void applyButtonState(boolean scanning) {
        mainHandler.post(() -> {
            if (txtStatus != null) {
                txtStatus.setText(scanning ? "● SCANNING" : "● NOT SCANNING");
                txtStatus.setTextColor(scanning ? 0xFF81C784 : 0xFF90A4AE);
            }
            if (btnStart != null) {
                btnStart.setVisibility(scanning ? View.GONE : View.VISIBLE);
            }
            if (btnStop != null) {
                btnStop.setVisibility(scanning ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void resetRiskCard() {
        mainHandler.post(() -> {
            if (riskCard != null) riskCard.setVisibility(View.GONE);
        });
    }

    private void updateRiskCard(RiskLabel label, int score, int confidence, String reason) {
        mainHandler.post(() -> {
            if (riskCard == null) return;
            riskCard.setVisibility(View.VISIBLE);
            String icon; int bgColor;
            switch (label) {
                case SAFE:      icon = "👍  SAFE";             bgColor = 0xFF1B5E20; break;
                case RISKY:     icon = "⚠️  RISKY";            bgColor = 0xFFE65100; break;
                case NOT_SAFE:  icon = "🚨  DANGER";            bgColor = 0xFFB71C1C; break;
                default:        icon = "🔍  ANALYZING…";       bgColor = 0xFF1A237E; break;
            }
            riskCard.setCardBackgroundColor(bgColor);
            if (tvRiskLabel != null) tvRiskLabel.setText(icon);
            if (tvRiskScore != null) tvRiskScore.setText("Confidence: " + confidence + "%");
            if (tvRiskReason != null) {
                boolean has = reason != null && !reason.isEmpty();
                tvRiskReason.setText(has ? reason : "");
                tvRiskReason.setVisibility(has ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter sf = new IntentFilter(CaptureServiceV2.ACTION_STATUS);
        IntentFilter rf = new IntentFilter(CaptureServiceV2.ACTION_RISK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, sf, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(riskReceiver,   rf, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, sf);
            registerReceiver(riskReceiver,   rf);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(riskReceiver);   } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        Button b = findViewById(R.id.btnOverlayPermission);
        if (b != null && Settings.canDrawOverlays(this)) b.setVisibility(View.GONE);
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
}
