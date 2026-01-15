package com.example.ghostlink;
import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.widget.TextView;
import android.os.Build;



public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GhostLink";

    private ActivityResultLauncher<Intent> screenCaptureLauncher;
    private MediaProjectionManager mpm;

    private TextView txtStatus;

private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (CaptureService.ACTION_STATUS.equals(intent.getAction())) {
            boolean scanning = intent.getBooleanExtra(CaptureService.EXTRA_IS_SCANNING, false);
            setStatus(scanning);
        }
    }
};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        txtStatus = findViewById(R.id.txtStatus);
        setStatus(false);


        // Get MediaProjectionManager once
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // Register result handler for the screen-capture permission prompt
        screenCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Log.d(TAG, "MediaProjection permission granted");

                        Intent svc = new Intent(MainActivity.this, CaptureService.class);
                        svc.setAction(CaptureService.ACTION_START);

                        // Pass permission result to the service
                        svc.putExtra(CaptureService.EXTRA_RESULT_CODE, result.getResultCode());
                        svc.putExtra(CaptureService.EXTRA_RESULT_DATA, result.getData());

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(svc);
                        } else {
                            startService(svc);
                        }
                    } else {
                        Log.d(TAG, "MediaProjection permission denied");
                    }
                }
        );

        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop  = findViewById(R.id.btnStop);

        // Start button: show the screen capture permission dialog
        btnStart.setOnClickListener(v -> {
            if (mpm == null) {
                Log.e(TAG, "MediaProjectionManager is null (should not happen).");
                return;
            }
            Intent captureIntent = mpm.createScreenCaptureIntent();
            screenCaptureLauncher.launch(captureIntent);
        });

        // Stop button: ask service to stop
        btnStop.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, CaptureService.class);
            i.setAction(CaptureService.ACTION_STOP);
            startService(i);
        });

        // Keep your edge-to-edge inset handling
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setStatus(boolean scanning) {
    if (txtStatus == null) return;

    if (scanning) {
        txtStatus.setText("Status: Scanning");
        txtStatus.setBackgroundColor(0xFF2E7D32); // green
    } else {
        txtStatus.setText("Status: Not scanning");
        txtStatus.setBackgroundColor(0xFF616161); // gray
    }
}
@Override
protected void onStart() {
    super.onStart();

    IntentFilter filter = new IntentFilter(CaptureService.ACTION_STATUS);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    } else {
        registerReceiver(statusReceiver, filter);
    }
}


@Override
protected void onStop() {
    super.onStop();
    try {
        unregisterReceiver(statusReceiver);
    } catch (IllegalArgumentException ignored) {
        // receiver wasn't registered
    }
}



}
