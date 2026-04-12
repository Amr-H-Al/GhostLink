package com.example.ghostlink;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class CaptureServiceV2 extends Service {

    private static final String TAG = "GhostLink";
    private static final String CHANNEL_ID = "ghostlink_capture_channel";
    private static final int NOTIF_ID = 1001;

    public static final String ACTION_START = "com.example.ghostlink.action.START";
    public static final String ACTION_STOP  = "com.example.ghostlink.action.STOP";
    public static final String ACTION_STATUS = "com.example.ghostlink.action.STATUS";
    public static final String ACTION_RISK = "com.example.ghostlink.action.RISK";

    public static final String EXTRA_IS_SCANNING = "extra_is_scanning";
    public static final String EXTRA_RISK_LABEL = "extra_risk_label";
    public static final String EXTRA_RISK_SCORE = "extra_risk_score";
    public static final String EXTRA_RISK_CONFIDENCE = "extra_risk_confidence";
    public static final String EXTRA_RISK_REASON = "extra_risk_reason";
    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_RESULT_DATA = "extra_result_data";

    private MediaProjectionManager mpm;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private HandlerThread captureThread;
    private Handler captureHandler;

    private long lastFrameMs = 0L;
    private long scanStartTimeMs = 0L;
    private static final long STARTUP_DELAY_MS = 2500;

    private OcrProcessor ocrProcessor;
    private RiskScorer riskScorer;
    private StatsManager statsManager;
    private OverlayViewManager overlayManager;

    private RiskLabel lastRecordedLabel = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannelIfNeeded();
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        ocrProcessor   = new OcrProcessor();
        riskScorer     = new RiskScorer();
        statsManager   = new StatsManager(this);
        overlayManager = new OverlayViewManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            broadcastScanning(false);
            stopCapturePipeline();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            int resultCode = (intent != null) ? intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) : Activity.RESULT_CANCELED;
            Intent resultData = (intent != null) ? intent.getParcelableExtra(EXTRA_RESULT_DATA) : null;

            if (resultCode != Activity.RESULT_OK || resultData == null) {
                broadcastScanning(false);
                stopSelf();
                return START_NOT_STICKY;
            }

            Notification notif = buildNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIF_ID, notif);
            }

            scanStartTimeMs = System.currentTimeMillis();
            startCapturePipeline(resultCode, resultData);
            overlayManager.show();
            broadcastScanning(true);
            return START_STICKY;
        }

        stopSelf();
        return START_NOT_STICKY;
    }

    private void startCapturePipeline(int resultCode, Intent resultData) {
        if (mpm == null) return;
        stopCapturePipeline();

        captureThread = new HandlerThread("GhostLinkCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        mediaProjection = mpm.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) {
            stopCapturePipeline();
            return;
        }

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                broadcastScanning(false);
                stopCapturePipeline();
                stopSelf();
            }
        }, captureHandler);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        imageReader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("GhostLinkDisplay",
                dm.widthPixels, dm.heightPixels, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, captureHandler);

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;

                long now = System.currentTimeMillis();
                if (now - lastFrameMs < 2000 || now - scanStartTimeMs < STARTUP_DELAY_MS) {
                    image.close();
                    return;
                }
                lastFrameMs = now;

                Bitmap bmp = imageToBitmap(image);
                image.close();
                if (bmp == null) return;

                ocrProcessor.process(bmp, new OcrProcessor.Callback() {
                    @Override
                    public void onSuccess(String text) {
                        try {
                            String cleaned = TextCleaner.clean(text);
                            RiskResult result = riskScorer.score(cleaned);
                            
                            // RECORD TO HISTORY ONLY IF LABEL CHANGED
                            if (result.getLabel() != RiskLabel.MORE_INFO_NEEDED && result.getLabel() != lastRecordedLabel) {
                                lastRecordedLabel = result.getLabel();
                                String snippet = cleaned.length() > 80 ? cleaned.substring(0, 80) + "..." : cleaned;
                                
                                String screenshotPath = null;
                                if (result.getLabel() != RiskLabel.SAFE) {
                                    screenshotPath = saveScreenshot(bmp);
                                }

                                statsManager.addHistoryItem(new HistoryItem(System.currentTimeMillis(), 
                                        result.getLabel().name(), result.getConfidencePercent(), 
                                        result.getTopReasonOrEmpty(), snippet, screenshotPath));
                            }
                            broadcastRisk(result);
                        } finally {
                            bmp.recycle();
                        }
                    }
                    @Override public void onFailure(Exception e) { bmp.recycle(); }
                });
            } catch (Exception e) {
                if (image != null) image.close();
            }
        }, captureHandler);
    }

    private String saveScreenshot(Bitmap bmp) {
        try {
            File dir = new File(getFilesDir(), "screenshots");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "scam_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream out = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 70, out);
            out.flush();
            out.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save screenshot", e);
            return null;
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return null;
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(), Bitmap.Config.ARGB_8888);
        buffer.rewind();
        bitmap.copyPixelsFromBuffer(buffer);
        Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
        bitmap.recycle();
        return cropped;
    }

    private void stopCapturePipeline() {
        if (imageReader != null) imageReader.setOnImageAvailableListener(null, null);
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
        if (captureThread != null) { captureThread.quitSafely(); captureThread = null; }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        broadcastScanning(false);
        stopCapturePipeline();
        if (overlayManager != null) overlayManager.hide();
        if (ocrProcessor != null) { try { ocrProcessor.close(); } catch (Exception ignored) {} }
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    private void broadcastScanning(boolean scanning) {
        Intent i = new Intent(ACTION_STATUS);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_IS_SCANNING, scanning);
        sendBroadcast(i);
        if (!scanning && overlayManager != null) overlayManager.hide();
    }

    private void broadcastRisk(RiskResult r) {
        if (overlayManager != null) overlayManager.update(r);
        Intent i = new Intent(ACTION_RISK);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_RISK_LABEL, r.getLabel().name());
        i.putExtra(EXTRA_RISK_SCORE, r.getScore());
        i.putExtra(EXTRA_RISK_CONFIDENCE, r.getConfidencePercent());
        i.putExtra(EXTRA_RISK_REASON, r.getTopReasonOrEmpty());
        sendBroadcast(i);
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "GhostLink", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, CaptureServiceV2.class).setAction(ACTION_STOP);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, flags);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GhostLink Active")
                .setContentText("Scanning for scams...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(0, "Stop", stopPendingIntent))
                .build();
    }
}
