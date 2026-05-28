package com.hieptran.hubnotification;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.Calendar;

public class CarHudService extends Service {
    private static final String CHANNEL_ID = "car_hud_channel";
    private static final int NOTIFICATION_ID = 101;
    private static final long DISCONNECT_AUTO_STOP_MS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private BleClient bleClient;
    private FusedLocationProviderClient fusedLocationClient;
    private long lastSpeedSentAt;
    private boolean hudRunning;
    private boolean bleReady;
    private boolean everReadyConnected;

    private final Runnable clockTask = new Runnable() {
        @Override
        public void run() {
            Calendar calendar = Calendar.getInstance();
            CarHudBus.publishClock(
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE)
            );
            handler.postDelayed(this, 30_000L);
        }
    };

    private final Runnable disconnectAutoStopTask = new Runnable() {
        @Override
        public void run() {
            if (!everReadyConnected || bleReady) {
                return;
            }
            broadcastStatus("BLE: disconnected for 30s, stopping HUD service", false, false);
            stopHud();
            stopSelf();
        }
    };

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            Location location = result.getLastLocation();
            if (location == null || !location.hasSpeed()) {
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastSpeedSentAt < 1000L) {
                return;
            }

            lastSpeedSentAt = now;
            int kmh = Math.max(0, Math.round(location.getSpeed() * 3.6f));
            CarHudBus.publishSpeed(kmh);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        bleClient = new BleClient(this, this::handleBleStatus);
        CarHudBus.init(bleClient);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : CarHudConstants.ACTION_START_HUD;

        if (CarHudConstants.ACTION_REQUEST_STATUS.equals(action)) {
            CarHudRuntimeStatus.Snapshot snapshot = CarHudRuntimeStatus.snapshot();
            broadcastStatus(snapshot.status, snapshot.connected, snapshot.ready);
            if (!hudRunning) {
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }

        if (CarHudConstants.ACTION_STOP_HUD.equals(action)) {
            broadcastStatus("BLE: stopping HUD service", inferConnected(null), bleReady);
            stopHud();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (CarHudConstants.ACTION_SEND_TEST_NAV.equals(action)) {
            CarHudBus.sendRaw("{\"t\":\"nav\",\"arr\":\"right\",\"d\":150,\"u\":\"m\",\"s\":\"Le Loi\"}");
            return START_STICKY;
        }

        if (CarHudConstants.ACTION_SEND_TEST_PAYLOAD.equals(action)) {
            String payload = intent != null ? intent.getStringExtra(CarHudConstants.EXTRA_TEST_PAYLOAD) : null;
            if (payload != null && !payload.trim().isEmpty()) {
                CarHudBus.sendRaw(payload);
            }
            return START_STICKY;
        }

        if (CarHudConstants.ACTION_SEND_DISPLAY_CONFIG.equals(action)) {
            sendDisplayConfig(intent);
            return START_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        startHud();
        return START_STICKY;
    }

    private void startHud() {
        hudRunning = true;
        broadcastStatus("BLE: starting HUD service", false, false);
        bleClient.start();
        startClockPublisher();
        publishBatteryOnce();
        startSpeedProvider();
    }

    private void stopHud() {
        hudRunning = false;
        bleReady = false;
        everReadyConnected = false;
        handler.removeCallbacks(disconnectAutoStopTask);
        stopSpeedProvider();
        handler.removeCallbacks(clockTask);
        bleClient.stop();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    private void sendDisplayConfig(Intent intent) {
        if (!bleReady) {
            broadcastStatus("Display config skipped: BLE not ready", inferConnected(null), false);
            return;
        }

        String mode = intent != null
                ? intent.getStringExtra(CarHudConstants.EXTRA_DISPLAY_MODE)
                : CarHudDisplayConfig.MODE_NORMAL;
        String flip = intent != null
                ? intent.getStringExtra(CarHudConstants.EXTRA_DISPLAY_FLIP)
                : CarHudDisplayConfig.FLIP_VERTICAL;
        int brightness = intent != null
                ? intent.getIntExtra(CarHudConstants.EXTRA_DISPLAY_BRIGHTNESS, CarHudDisplayConfig.getBrightness(this))
                : CarHudDisplayConfig.getBrightness(this);

        boolean hudMode = CarHudDisplayConfig.MODE_HUD.equals(mode);
        CarHudBus.publishDisplayConfig(hudMode, flip, brightness);
        broadcastStatus("Display config sent: " + (hudMode ? "HUD" : "Normal")
                + ", flip=" + CarHudDisplayConfig.sanitizeFlip(flip)
                + ", br=" + CarHudDisplayConfig.clampBrightness(brightness), true, true);
    }

    private void startClockPublisher() {
        handler.removeCallbacks(clockTask);
        clockTask.run();
    }

    private void publishBatteryOnce() {
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) {
            return;
        }
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) {
            return;
        }
        int batteryPct = Math.round((level * 100f) / scale);
        CarHudBus.publishBattery(batteryPct);
    }

    @SuppressLint("MissingPermission")
    private void startSpeedProvider() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return;
        }

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .build();
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void stopSpeedProvider() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Car HUD active")
                .setContentText("Streaming to ESP32 via BLE")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Car HUD",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean inferConnected(String status) {
        if (status == null) {
            return bleReady;
        }
        String lower = status.toLowerCase();
        return lower.contains("ready")
                || lower.contains("services discovered")
                || lower.contains("discovering services")
                || (lower.contains("connected") && !lower.contains("disconnected"));
    }

    private boolean inferReady(String status) {
        return status != null && status.toLowerCase().startsWith("ble: ready");
    }

    private void handleBleStatus(String status) {
        String safeStatus = status == null ? "BLE: status unavailable" : status;
        String lower = safeStatus.toLowerCase();
        boolean ready = bleReady;

        if (inferReady(safeStatus)) {
            ready = true;
        } else if (statusIndicatesNotReady(lower)) {
            ready = false;
        }

        bleReady = ready;
        boolean connected = ready || inferConnected(safeStatus);

        if (ready) {
            everReadyConnected = true;
            handler.removeCallbacks(disconnectAutoStopTask);
        } else if (lower.contains("disconnected") && everReadyConnected) {
            handler.removeCallbacks(disconnectAutoStopTask);
            handler.postDelayed(disconnectAutoStopTask, DISCONNECT_AUTO_STOP_MS);
        } else if (lower.contains("stopped")) {
            handler.removeCallbacks(disconnectAutoStopTask);
        }

        broadcastStatus(safeStatus, connected, ready);
    }

    private boolean statusIndicatesNotReady(String lowerStatus) {
        return lowerStatus.contains("disconnected")
                || lowerStatus.contains("stopped")
                || lowerStatus.contains("scanning")
                || lowerStatus.contains("connecting")
                || lowerStatus.contains("requesting mtu")
                || lowerStatus.contains("discovering services")
                || lowerStatus.contains("services discovered")
                || lowerStatus.contains("service not found")
                || lowerStatus.contains("rx characteristic not found")
                || lowerStatus.contains("missing bluetooth")
                || lowerStatus.contains("adapter disabled")
                || lowerStatus.contains("scanner unavailable")
                || lowerStatus.contains("bluetoothmanager unavailable");
    }

    private void broadcastStatus(String status, boolean connected, boolean ready) {
        CarHudRuntimeStatus.update(status, connected, ready, hudRunning);
        Intent statusIntent = new Intent(CarHudConstants.ACTION_STATUS_UPDATE);
        statusIntent.setPackage(getPackageName());
        statusIntent.putExtra(CarHudConstants.EXTRA_BLE_STATUS, status);
        statusIntent.putExtra(CarHudConstants.EXTRA_BLE_CONNECTED, connected);
        statusIntent.putExtra(CarHudConstants.EXTRA_BLE_READY, ready);
        statusIntent.putExtra(CarHudConstants.EXTRA_HUD_RUNNING, hudRunning);
        sendBroadcast(statusIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopHud();
        super.onDestroy();
    }
}
