package com.example.ghostlink;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class CaptureService extends Service {

    private static final String TAG = "GhostLink";
    private static final String CHANNEL_ID = "ghostlink_capture_channel";
    private static final int NOTIF_ID = 1001;

    public static final String ACTION_START = "com.example.ghostlink.action.START";
    public static final String ACTION_STOP  = "com.example.ghostlink.action.STOP";

    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_RESULT_DATA = "extra_result_data";

    public static final String ACTION_STATUS = "com.example.ghostlink.action.STATUS";
    public static final String EXTRA_IS_SCANNING = "extra_is_scanning";


    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannelIfNeeded();
        Log.d(TAG, "CaptureService onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : null;
        Log.d(TAG, "CaptureService onStartCommand() action=" + action);

        // Stop request
        if (ACTION_STOP.equals(action)) {
            broadcastScanning(false);

            stopSelf();
            return START_NOT_STICKY;
        }

        // Only start if we have MediaProjection permission result
        if (ACTION_START.equals(action)) {
            int resultCode = (intent != null)
                    ? intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                    : Activity.RESULT_CANCELED;

            Intent resultData = (intent != null)
                    ? intent.getParcelableExtra(EXTRA_RESULT_DATA)
                    : null;

            if (resultCode != Activity.RESULT_OK || resultData == null) {
                Log.e(TAG, "Missing MediaProjection permission data. Not starting foreground service.");
                stopSelf();
                return START_NOT_STICKY;
            }

            Notification notif = buildNotification();

            // On Android 10+ you can (and should) specify the FGS type in startForeground(...)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIF_ID, notif);
            }
            broadcastScanning(true);


            // Phase 1 next: create MediaProjection + VirtualDisplay + ImageReader
            return START_STICKY;
        }

        // Unknown action or null intent -> don't run
        stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        broadcastScanning(false);
        super.onDestroy();
        Log.d(TAG, "CaptureService onDestroy()");
        // Phase 1 later: release MediaProjection / VirtualDisplay / ImageReader here
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

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

    private Notification buildNotification() {
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
                .setContentText("Tap Stop to end capture")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.ic_media_pause,
                        "Stop",
                        stopPendingIntent
                ))
                .build();
    }
    private void broadcastScanning(boolean scanning) {
    Intent i = new Intent(ACTION_STATUS);
    i.setPackage(getPackageName()); // keeps broadcast inside your app
    i.putExtra(EXTRA_IS_SCANNING, scanning);
    sendBroadcast(i);
    }
}