package com.example.netoverlay;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "net_overlay_channel";
    private static final int NOTIF_ID = 1;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;

    private TextView tvNetType;
    private SignalBarsView signalBars;

    private float scaleFactor = 1.0f;
    private ScaleGestureDetector scaleGestureDetector;

    // Флаг: находится ли соединение в режиме 5G NSA (LTE + NR-агрегация)
    private volatile boolean isNrOverride = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflater = LayoutInflater.from(this);
        overlayView = inflater.inflate(R.layout.overlay_layout, null);

        tvNetType = overlayView.findViewById(R.id.tvNetType);
        signalBars = overlayView.findViewById(R.id.signalBars);
        View btnPlus = overlayView.findViewById(R.id.btnPlus);
        View btnMinus = overlayView.findViewById(R.id.btnMinus);
        View btnClose = overlayView.findViewById(R.id.btnClose);

        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 150;

        windowManager.addView(overlayView, params);

        btnPlus.setOnClickListener(v -> applyScale(scaleFactor + 0.15f));
        btnMinus.setOnClickListener(v -> applyScale(scaleFactor - 0.15f));
        btnClose.setOnClickListener(v -> stopSelf());

        setupDragAndScale();
        setupTelephonyListener();
    }

    private void setupDragAndScale() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                applyScale(scaleFactor * detector.getScaleFactor());
                return true;
            }
        });

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleGestureDetector.onTouchEvent(event);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (event.getPointerCount() == 1) {
                            int dx = (int) (event.getRawX() - initialTouchX);
                            int dy = (int) (event.getRawY() - initialTouchY);
                            if (Math.abs(dx) > 5 || Math.abs(dy) > 5) moved = true;
                            params.x = initialX + dx;
                            params.y = initialY + dy;
                            windowManager.updateViewLayout(overlayView, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        return moved;
                    default:
                        return false;
                }
            }
        });
    }

    private void applyScale(float newScale) {
        scaleFactor = Math.max(0.6f, Math.min(newScale, 3.0f));
        overlayView.setScaleX(scaleFactor);
        overlayView.setScaleY(scaleFactor);
    }

    private void setupTelephonyListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                int level = signalStrength.getLevel(); // 0..4
                signalBars.setLevel(level);
            }

            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                updateNetworkTypeLabel(networkType);
            }

            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                if (telephonyManager != null) {
                    try {
                        updateNetworkTypeLabel(telephonyManager.getDataNetworkType());
                    } catch (SecurityException ignored) {
                    }
                }
            }

            // Доступно с API 30: определяет режим 5G NSA (агрегация LTE+NR)
            @Override
            public void onDisplayInfoChanged(TelephonyDisplayInfo telephonyDisplayInfo) {
                int override = telephonyDisplayInfo.getOverrideNetworkType();
                isNrOverride = (override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
                        || override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED);
                updateNetworkTypeLabel(telephonyDisplayInfo.getNetworkType());
            }
        };

        int mask = PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                | PhoneStateListener.LISTEN_SERVICE_STATE;

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                telephonyManager.listen(phoneStateListener, mask);
            } catch (SecurityException e) {
                tvNetType.setText("Нет доступа");
            }
        } else {
            tvNetType.setText("Нет разрешения");
        }
    }

    private void updateNetworkTypeLabel(int networkType) {
        String label = networkTypeToLabel(networkType);
        if (isNrOverride && !"5G".equals(label)) {
            label = "5G";
        }
        tvNetType.setText(label);
    }

    private String networkTypeToLabel(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:
            case TelephonyManager.NETWORK_TYPE_IWLAN:
                return "4G";
            case TelephonyManager.NETWORK_TYPE_NR:
                return "5G";
            default:
                return "—";
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Net Overlay Service", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private android.app.Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Net Overlay активен")
                .setContentText("Показывается статус сети поверх экрана")
                .setSmallIcon(android.R.drawable.stat_sys_signal_4)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(android.content.Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
        }
    }
}
