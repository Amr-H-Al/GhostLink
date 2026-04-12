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

import java.nio.ByteBuffer;
import java.util.List;

/**
 * CaptureService
 *
 * Phase 1 goal:
 * - Run as a FOREGROUND SERVICE so Android allows continuous background work
 * - Use MediaProjection to capture screen frames
 * - Create a VirtualDisplay that outputs into an ImageReader
 * - Convert frames to Bitmap ~ once per second and log that it worked
 *
 * IMPORTANT (newer Android / targetSdk):
 * - You MUST register a MediaProjection.Callback BEFORE calling createVirtualDisplay()
 *   or you'll crash with:
 *   "Must register a callback before starting capture..."
 */
public class CaptureService extends Service {

    // ===== Logging / notification constants =====
    private static final String TAG = "GhostLink";
    private static final String CHANNEL_ID = "ghostlink_capture_channel";
    private static final int NOTIF_ID = 1001;

    // ===== Actions used by MainActivity / Notification button =====
    public static final String ACTION_START = "com.example.ghostlink.action.START";
    public static final String ACTION_STOP  = "com.example.ghostlink.action.STOP";

    // ===== Broadcast used to update the in-app badge (Scanning / Not scanning) =====
    public static final String ACTION_STATUS = "com.example.ghostlink.action.STATUS";
    public static final String EXTRA_IS_SCANNING = "extra_is_scanning";

    // ===== Broadcast for Phase 2 results (Phase 3 overlay can listen later) =====
    public static final String ACTION_RISK = "com.example.ghostlink.action.RISK";
    public static final String EXTRA_RISK_LABEL = "extra_risk_label";
    public static final String EXTRA_RISK_SCORE = "extra_risk_score";
    public static final String EXTRA_RISK_CONFIDENCE = "extra_risk_confidence";
    public static final String EXTRA_RISK_REASON = "extra_risk_reason";

    // ===== Extras passed from MainActivity after user grants screen-capture permission =====
    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_RESULT_DATA = "extra_result_data";

    // ===== MediaProjection / capture pipeline objects =====
    private MediaProjectionManager mpm;    // used to convert permission data -> MediaProjection
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    // We do all capture work on a background thread (NOT the main/UI thread)
    private HandlerThread captureThread;
    private Handler captureHandler;

    // Used to throttle frame processing to about 1 frame per second
    private long lastFrameMs = 0L;

    // Delay first scan by 2.5 seconds so the app has time to go to background
    // before we capture any frames — prevents reading our own UI as a "scam"
    private long scanStartTimeMs = 0L;
    private static final long STARTUP_DELAY_MS = 2500;

    // ===== Phase 2 components =====
    private OcrProcessor ocrProcessor;
    private RiskScorer riskScorer;

    // ===== Phase 3: overlay owned by the service so it stays alive in background =====
    private OverlayViewManager overlayManager;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannelIfNeeded();
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        ocrProcessor   = new OcrProcessor();
        riskScorer     = new RiskScorer();
        overlayManager = new OverlayViewManager(this);
        Log.d(TAG, "CaptureService onCreate()");
    }

    /**
     * onStartCommand is called whenever the service is started/stopped via Intent.
     * MainActivity sends ACTION_START with permission data
     * Notification stop button sends ACTION_STOP
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : null;
        Log.d(TAG, "CaptureService onStartCommand() action=" + action);

        // ===== STOP =====
        if (ACTION_STOP.equals(action)) {
            broadcastScanning(false);     // update badge in app
            stopCapturePipeline();        // release all resources
            stopSelf();                   // stop service instance
            return START_NOT_STICKY;
        }

        // ===== START =====
        if (ACTION_START.equals(action)) {

            // Pull the MediaProjection permission result from the Intent
            int resultCode = (intent != null)
                    ? intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                    : Activity.RESULT_CANCELED;

            Intent resultData = (intent != null)
                    ? intent.getParcelableExtra(EXTRA_RESULT_DATA)
                    : null;

            // If we don't have valid permission data, we can't capture
            if (resultCode != Activity.RESULT_OK || resultData == null) {
                Log.e(TAG, "Missing MediaProjection permission data. Not starting.");
                broadcastScanning(false);
                stopSelf();
                return START_NOT_STICKY;
            }

            // 1) Start foreground service with persistent notification (required)
            Notification notif = buildNotification();

            // For targetSdk >= 29, specify service type to match manifest:
            // foregroundServiceType="mediaProjection"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                        NOTIF_ID,
                        notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );
            } else {
                startForeground(NOTIF_ID, notif);
            }

            // 2) Start MediaProjection capture pipeline
            scanStartTimeMs = System.currentTimeMillis();
            startCapturePipeline(resultCode, resultData);

            // 3) Show overlay (service owns it — stays visible over any app)
            overlayManager.show();

            // 4) Update badge
            broadcastScanning(true);

            return START_STICKY;
        }

        // Unknown action -> stop
        stopSelf();
        return START_NOT_STICKY;
    }

    /**
     * Starts the MediaProjection capture pipeline:
     * - Create a background thread (captureHandler)
     * - Create MediaProjection from permission data
     * - Register callback BEFORE creating virtual display (required on newer Android)
     * - Create ImageReader + VirtualDisplay
     * - When frames arrive, convert to Bitmap ~1/sec and log it
     */
    private void startCapturePipeline(int resultCode, Intent resultData) {
        if (mpm == null) {
            Log.e(TAG, "MediaProjectionManager is null");
            return;
        }

        // If pipeline is already running, stop it first (prevents leaks)
        stopCapturePipeline();

        // ---- 1) Start background thread FIRST so we have captureHandler ----
        captureThread = new HandlerThread("GhostLinkCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        // ---- 2) Create MediaProjection from permission data ----
        mediaProjection = mpm.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) {
            Log.e(TAG, "Failed to obtain MediaProjection");
            stopCapturePipeline();
            return;
        }

        // ---- 3) REQUIRED: Register callback BEFORE createVirtualDisplay() ----
        // Newer Android versions enforce this for resource cleanup & state tracking.
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                // System stopped projection (user revoked permission, etc.)
                Log.d(TAG, "MediaProjection stopped by system");
                broadcastScanning(false);
                stopCapturePipeline();
                stopSelf();
            }
        }, captureHandler);

        // ---- 4) Determine capture size ----
        // This captures at full screen size (can downscale later if needed)
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int densityDpi = dm.densityDpi;

        // ---- 5) Create ImageReader ----
        // RGBA_8888 gives easy Bitmap conversion.
        // MaxImages=2 is enough for our 1fps throttle (keeps memory low)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

        // ---- 6) Create VirtualDisplay ----
        // VirtualDisplay "mirrors" the screen into the ImageReader surface.
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "GhostLinkVirtualDisplay",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                captureHandler
        );

        // ---- 7) Receive frames from ImageReader ----
        // This callback runs on captureHandler's thread, not main thread.
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;

            try {
                // Always take the newest frame available
                image = reader.acquireLatestImage();
                if (image == null) return;

                // Throttle to about 1 frame per second (Phase 1 requirement)
                long now = System.currentTimeMillis();
                if (now - lastFrameMs < 1000) {
                    return;
                }

                // Wait for the startup delay so the app goes to background first.
                // This prevents GhostLink from scanning its OWN UI text.
                if (now - scanStartTimeMs < STARTUP_DELAY_MS) {
                    Log.d(TAG, "Startup delay — skipping frame");
                    return;
                }

                lastFrameMs = now;

                // Convert Image -> Bitmap (so later phases can run processing on it)
                Bitmap bmp = imageToBitmap(image);
                if (bmp == null) {
                    Log.d(TAG, "Frame captured but bitmap conversion returned null");
                    return;
                }

                Log.d(TAG, "Frame captured: " + bmp.getWidth() + "x" + bmp.getHeight() + " @ " + now);

                // Phase 2: OCR + Risk scoring (in-memory only)
                ocrProcessor.process(bmp, new OcrProcessor.Callback() {
                    @Override
                    public void onSuccess(String text) {
                        try {
                            String cleaned = TextCleaner.clean(text);
                            RiskResult result = riskScorer.score(cleaned);

                            Log.d(TAG, "Risk=" + result.getLabel()
                                    + " score=" + result.getScore()
                                    + " reason=" + result.getTopReasonOrEmpty());

                            broadcastRisk(result);
                        } catch (Exception e) {
                            Log.e(TAG, "Risk scoring failed", e);
                        } finally {
                            bmp.recycle();
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "OCR failed", e);
                        bmp.recycle();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error reading frame", e);
            } finally {
                // MUST close images or ImageReader will stall/crash
                if (image != null) image.close();
            }
        }, captureHandler);

        Log.d(TAG, "Capture pipeline started (" + width + "x" + height + ")");
    }

    /**
     * Converts an ImageReader Image (RGBA_8888) into a Bitmap.
     *
     * NOTE:
     * Image rows can have padding (rowStride > width * pixelStride),
     * so we create a bitmap big enough for the padding, copy buffer, then crop.
     */
    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return null;

        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();

        // Create bitmap that includes row padding
        Bitmap bitmap = Bitmap.createBitmap(
                image.getWidth() + rowPadding / pixelStride,
                image.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        buffer.rewind();
        bitmap.copyPixelsFromBuffer(buffer);

        // Crop away padding to exact screen dimensions
        Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
        bitmap.recycle();
        return cropped;
    }

    /**
     * Stops and releases everything created in startCapturePipeline().
     * This prevents memory leaks and allows restart without crashing.
     */
    private void stopCapturePipeline() {
        // Stop frame callbacks first
        if (imageReader != null) {
            try {
                imageReader.setOnImageAvailableListener(null, null);
            } catch (Exception ignored) {}
        }

        // Release VirtualDisplay
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Exception ignored) {}
            virtualDisplay = null;
        }

        // Close ImageReader
        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (Exception ignored) {}
            imageReader = null;
        }

        // Stop MediaProjection (also triggers callback sometimes)
        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Exception ignored) {}
            mediaProjection = null;
        }

        // Stop background thread
        if (captureThread != null) {
            try {
                captureThread.quitSafely();
            } catch (Exception ignored) {}
            captureThread = null;
            captureHandler = null;
        }

        lastFrameMs = 0L;
        scanStartTimeMs = 0L;
        Log.d(TAG, "Capture pipeline stopped");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CaptureService onDestroy()");
        broadcastScanning(false);
        stopCapturePipeline();
        if (overlayManager != null) overlayManager.hide();
        if (ocrProcessor != null) {
            try { ocrProcessor.close(); } catch (Exception ignored) {}
            ocrProcessor = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Broadcast scanning state so MainActivity can update the badge.
     */
    private void broadcastScanning(boolean scanning) {
        Intent i = new Intent(ACTION_STATUS);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_IS_SCANNING, scanning);
        sendBroadcast(i);
        if (!scanning && overlayManager != null) overlayManager.hide();
    }

    /**
     * Broadcast Phase 2 risk results AND update the overlay directly.
     * The overlay is updated here (in the service) so it works even when
     * MainActivity is in the background or not visible.
     */
    private void broadcastRisk(RiskResult r) {
        if (r == null) return;

        // 1) Update the overlay bar immediately — no broadcast needed for this
        if (overlayManager != null) {
            overlayManager.update(r);
        }

        // 2) Also broadcast so MainActivity can update its in-app risk card
        Intent i = new Intent(ACTION_RISK);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_RISK_LABEL, r.getLabel().name());
        i.putExtra(EXTRA_RISK_SCORE, r.getScore());
        i.putExtra(EXTRA_RISK_CONFIDENCE, r.getConfidencePercent());
        i.putExtra(EXTRA_RISK_REASON, r.getTopReasonOrEmpty());
        sendBroadcast(i);
    }

    /**
     * Required on Android O+ so the foreground notification can be shown.
     */
    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GhostLink Capture",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Foreground service for screen capture");

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    /**
     * Builds the persistent foreground notification.
     * Includes a Stop action that triggers ACTION_STOP.
     */
    private Notification buildNotification() {
        // Intent for Stop button in notification
        Intent stopIntent = new Intent(this, CaptureService.class);
        stopIntent.setAction(ACTION_STOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, flags
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GhostLink is running")
                .setContentText("Capturing screen frames (~1/sec). Tap Stop to end.")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.ic_media_pause,
                        "Stop",
                        stopPendingIntent
                ))
                .build();
    }
}
