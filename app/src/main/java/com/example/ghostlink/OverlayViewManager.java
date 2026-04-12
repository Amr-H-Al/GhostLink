package com.example.ghostlink;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

/**
 * OverlayViewManager
 *
 * Owned by CaptureServiceV2. Updated to be clearly visible below the Pixel 7 notch
 * and allow touch-through to the apps below.
 */
public class OverlayViewManager {

    private static final String TAG = "GhostLink";

    private final Context context;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private View overlayView;
    private boolean isShowing = false;

    private static final int COLOR_SAFE      = 0xCC1B5E20;
    private static final int COLOR_RISKY     = 0xCCE65100;
    private static final int COLOR_NOT_SAFE  = 0xCCB71C1C;
    private static final int COLOR_ANALYZING = 0xCC1A237E;

    public OverlayViewManager(Context context) {
        this.context       = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        mainHandler.post(() -> {
            if (isShowing || windowManager == null) return;
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Overlay permission not granted");
                return;
            }
            try {
                overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_bar, null);

                int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        overlayType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                );
                
                params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                
                // Increased Y offset to 200px to ensure it clears the notch area clearly on Pixel 7
                params.y = 200; 

                windowManager.addView(overlayView, params);
                isShowing = true;

                applyState(RiskLabel.MORE_INFO_NEEDED, 0, null);
                Log.d(TAG, "Overlay shown (touch-through, below notch)");
            } catch (Exception e) {
                Log.e(TAG, "Failed to show overlay", e);
            }
        });
    }

    public void update(RiskResult result) {
        if (result == null) return;
        update(result.getLabel(), result.getConfidencePercent(), result.getTopReasonOrEmpty());
    }

    public void update(RiskLabel label, int confidence, String reason) {
        mainHandler.post(() -> applyState(label, confidence, reason));
    }

    private void applyState(RiskLabel label, int confidence, String reason) {
        if (!isShowing || overlayView == null) return;
        try {
            View bar          = overlayView.findViewById(R.id.overlayBar);
            TextView tvIcon   = overlayView.findViewById(R.id.tvIcon);
            TextView tvLabel  = overlayView.findViewById(R.id.tvLabel);
            TextView tvReason = overlayView.findViewById(R.id.tvReason);
            TextView tvConf   = overlayView.findViewById(R.id.tvConfidence);

            int    bgColor;
            String icon;
            String labelText;
            boolean showReason;

            switch (label) {
                case SAFE:
                    bgColor    = COLOR_SAFE;
                    icon       = "👍";
                    labelText  = "SAFE";
                    showReason = reason != null && !reason.isEmpty();
                    break;
                case RISKY:
                    bgColor    = COLOR_RISKY;
                    icon       = "⚠️";
                    labelText  = "RISKY";
                    showReason = reason != null && !reason.isEmpty();
                    break;
                case NOT_SAFE:
                    bgColor    = COLOR_NOT_SAFE;
                    icon       = "🚨";
                    labelText  = "DANGER";
                    showReason = reason != null && !reason.isEmpty();
                    break;
                default:
                    bgColor    = COLOR_ANALYZING;
                    icon       = "🔍";
                    labelText  = "ANALYZING…";
                    showReason = false;
                    reason     = null;
                    confidence = 0;
                    break;
            }

            if (bar != null) {
                ViewCompat.setBackgroundTintList(bar, ColorStateList.valueOf(bgColor));
            }
            if (tvIcon != null) tvIcon.setText(icon);
            if (tvLabel!= null) tvLabel.setText(labelText);

            if (tvReason != null) {
                if (showReason) {
                    tvReason.setText(reason);
                    tvReason.setVisibility(View.VISIBLE);
                } else {
                    tvReason.setText("");
                    tvReason.setVisibility(View.GONE);
                }
            }

            if (tvConf != null) {
                if (confidence > 0) {
                    tvConf.setText(confidence + "%");
                    tvConf.setVisibility(View.VISIBLE);
                } else {
                    tvConf.setText("");
                    tvConf.setVisibility(View.GONE);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to update overlay", e);
        }
    }

    public void hide() {
        mainHandler.post(() -> {
            if (!isShowing || overlayView == null || windowManager == null) return;
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
            overlayView = null;
            isShowing   = false;
        });
    }

    public boolean isShowing() {
        return isShowing;
    }
}
