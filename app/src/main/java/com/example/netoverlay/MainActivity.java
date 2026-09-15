package com.example.netoverlay;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PHONE_STATE = 100;
    private static final int REQ_NOTIFICATIONS = 101;

    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);

        Button btnGrantOverlay = findViewById(R.id.btnGrantOverlay);
        Button btnGrantPhoneState = findViewById(R.id.btnGrantPhoneState);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        btnGrantOverlay.setOnClickListener(v -> requestOverlayPermission());
        btnGrantPhoneState.setOnClickListener(v -> requestPhoneStatePermission());

        btnStart.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Сначала разрешите показ поверх экранов", Toast.LENGTH_LONG).show();
                requestOverlayPermission();
                return;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Сначала разрешите доступ к состоянию сети", Toast.LENGTH_LONG).show();
                requestPhoneStatePermission();
                return;
            }
            Intent svc = new Intent(this, OverlayService.class);
            ContextCompat.startForegroundService(this, svc);
            Toast.makeText(this, "Плавающее окно запущено", Toast.LENGTH_SHORT).show();
        });

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            Toast.makeText(this, "Остановлено", Toast.LENGTH_SHORT).show();
        });

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            }
        }

        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean overlayOk = Settings.canDrawOverlays(this);
        boolean phoneOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
        tvStatus.setText("Показ поверх экранов: " + (overlayOk ? "разрешено" : "нет") +
                "\nДоступ к сети: " + (phoneOk ? "разрешено" : "нет"));
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void requestPhoneStatePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_PHONE_STATE}, REQ_PHONE_STATE);
    }
}
