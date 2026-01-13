package com.example.ghostlink;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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

        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Default behavior: start foreground with a persistent notification
        startForeground(NOTIF_ID, buildNotification());

        // Phase 1 later: MediaProjection setup + VirtualDisplay + ImageReader

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
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
        // Stop action intent
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
}
