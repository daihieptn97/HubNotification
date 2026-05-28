package com.hieptran.hubnotification;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;

public class MainActivity extends AppCompatActivity {
    private TextView txtPermissionStatus;
    private TextView txtNotificationAccessStatus;
    private TextView txtBleChip;
    private TextView txtConnectionProgress;
    private TextView txtBleConnectedStatus;
    private TextView txtBleStatus;
    private TextView txtBleTimeline;
    private TextView txtDisplayConfigHint;
    private TextView txtBrightnessValue;
    private MaterialButtonToggleGroup toggleHudMode;
    private MaterialButtonToggleGroup toggleFlip;
    private Slider sliderBrightness;
    private View connectionProgressBar;
    private Button btnStartHud;
    private Button btnStopHud;
    private boolean bleReady;
    private boolean hudRunning;
    private boolean suppressDisplayConfigCallbacks;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder bleTimeline = new StringBuilder();
    private String lastTimelineStatus;

    private final Runnable startStatusFollowUp = new Runnable() {
        @Override
        public void run() {
            if (hudRunning && !bleReady) {
                requestServiceStatus();
            }
        }
    };

    private final Runnable stopStatusFallback = new Runnable() {
        @Override
        public void run() {
            String currentStatus = txtBleStatus.getText() == null ? "" : txtBleStatus.getText().toString().toLowerCase();
            if (!bleReady && (hudRunning || currentStatus.contains("stopping") || currentStatus.contains("stop requested"))) {
                applyBleStatus("BLE: stopped", false, false, false, true);
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                updatePermissionStatus();
            });

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!CarHudConstants.ACTION_STATUS_UPDATE.equals(intent.getAction())) {
                return;
            }
            String bleStatus = intent.getStringExtra(CarHudConstants.EXTRA_BLE_STATUS);
            if (!TextUtils.isEmpty(bleStatus)) {
                boolean connected = intent.getBooleanExtra(CarHudConstants.EXTRA_BLE_CONNECTED, false);
                boolean ready = intent.getBooleanExtra(CarHudConstants.EXTRA_BLE_READY, inferReady(bleStatus));
                boolean running = intent.getBooleanExtra(CarHudConstants.EXTRA_HUD_RUNNING, ready || connected);
                applyBleStatus(bleStatus, connected, ready, running, true);
            }
        }
    };

    private final CarHudRuntimeStatus.Listener runtimeStatusListener = snapshot -> runOnUiThread(() ->
            applyBleStatus(snapshot.status, snapshot.connected, snapshot.ready, snapshot.running, true)
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        txtPermissionStatus = findViewById(R.id.txtPermissionStatus);
        txtNotificationAccessStatus = findViewById(R.id.txtNotificationAccessStatus);
        txtBleChip = findViewById(R.id.txtBleChip);
        txtConnectionProgress = findViewById(R.id.txtConnectionProgress);
        txtBleConnectedStatus = findViewById(R.id.txtBleConnectedStatus);
        txtBleStatus = findViewById(R.id.txtBleStatus);
        txtBleTimeline = findViewById(R.id.txtBleTimeline);
        txtDisplayConfigHint = findViewById(R.id.txtDisplayConfigHint);
        txtBrightnessValue = findViewById(R.id.txtBrightnessValue);
        toggleHudMode = findViewById(R.id.toggleHudMode);
        toggleFlip = findViewById(R.id.toggleFlip);
        sliderBrightness = findViewById(R.id.sliderBrightness);
        connectionProgressBar = findViewById(R.id.connectionProgressBar);
        Button btnRequestPermissions = findViewById(R.id.btnRequestPermissions);
        Button btnOpenNotificationAccess = findViewById(R.id.btnOpenNotificationAccess);
        Button btnManageNotificationApps = findViewById(R.id.btnManageNotificationApps);
        Button btnOpenDebugLogs = findViewById(R.id.btnOpenDebugLogs);
        Button btnOpenLogManager = findViewById(R.id.btnOpenLogManager);
        Button btnOpenNavTest = findViewById(R.id.btnOpenNavTest);
        btnStartHud = findViewById(R.id.btnStartHud);
        btnStopHud = findViewById(R.id.btnStopHud);
        Button btnSendTestNav = findViewById(R.id.btnSendTestNav);

        btnRequestPermissions.setOnClickListener(v -> requestRuntimePermissions());
        btnOpenNotificationAccess.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });
        btnManageNotificationApps.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationAppSettingsActivity.class);
            startActivity(intent);
        });
        btnOpenDebugLogs.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationDebugLogActivity.class);
            startActivity(intent);
        });
        btnOpenLogManager.setOnClickListener(v -> {
            Intent intent = new Intent(this, LogManagerActivity.class);
            startActivity(intent);
        });
        btnOpenNavTest.setOnClickListener(v -> {
            Intent intent = new Intent(this, NavTestActivity.class);
            startActivity(intent);
        });
        btnStartHud.setOnClickListener(v -> startHudService());
        btnStopHud.setOnClickListener(v -> stopHudService());
        btnSendTestNav.setOnClickListener(v -> sendTestNav());

        setupDisplayConfigControls();
        updatePermissionStatus();
        updateNotificationAccessStatus();
        txtBleConnectedStatus.setText("BLE connected: NO");
        txtBleStatus.setText("BLE: idle");
        txtBleTimeline.setText("No BLE events yet.");
        CarHudRuntimeStatus.Snapshot snapshot = CarHudRuntimeStatus.snapshot();
        applyBleStatus(snapshot.status, snapshot.connected, snapshot.ready, snapshot.running, false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        CarHudRuntimeStatus.registerListener(runtimeStatusListener);
        ContextCompat.registerReceiver(
                this,
                statusReceiver,
                new IntentFilter(CarHudConstants.ACTION_STATUS_UPDATE),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        requestServiceStatus();
    }

    @Override
    protected void onStop() {
        super.onStop();
        CarHudRuntimeStatus.unregisterListener(runtimeStatusListener);
        unregisterReceiver(statusReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
        updateNotificationAccessStatus();
        CarHudRuntimeStatus.Snapshot snapshot = CarHudRuntimeStatus.snapshot();
        applyBleStatus(snapshot.status, snapshot.connected, snapshot.ready, snapshot.running, false);
        requestServiceStatus();
    }

    private void requestRuntimePermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.READ_PHONE_STATE);
        permissions.add(Manifest.permission.READ_CONTACTS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        permissionLauncher.launch(permissions.toArray(new String[0]));
    }

    private void startHudService() {
        handler.removeCallbacks(startStatusFollowUp);
        handler.removeCallbacks(stopStatusFallback);
        applyBleStatus("BLE: start requested", false, false, true, true);
        Intent intent = new Intent(this, CarHudService.class);
        intent.setAction(CarHudConstants.ACTION_START_HUD);
        ContextCompat.startForegroundService(this, intent);
        handler.postDelayed(startStatusFollowUp, 1200L);
        handler.postDelayed(startStatusFollowUp, 4500L);
    }

    private void stopHudService() {
        handler.removeCallbacks(startStatusFollowUp);
        handler.removeCallbacks(stopStatusFallback);
        applyBleStatus("BLE: stop requested", false, false, true, true);
        Intent intent = new Intent(this, CarHudService.class);
        intent.setAction(CarHudConstants.ACTION_STOP_HUD);
        startService(intent);
        handler.postDelayed(startStatusFollowUp, 700L);
        handler.postDelayed(stopStatusFallback, 2500L);
    }

    private void requestServiceStatus() {
        CarHudRuntimeStatus.Snapshot snapshot = CarHudRuntimeStatus.snapshot();
        if (!hudRunning && !snapshot.running) {
            return;
        }
        Intent intent = new Intent(this, CarHudService.class);
        intent.setAction(CarHudConstants.ACTION_REQUEST_STATUS);
        startService(intent);
    }

    private void sendTestNav() {
        Intent intent = new Intent(this, CarHudService.class);
        intent.setAction(CarHudConstants.ACTION_SEND_TEST_NAV);
        startService(intent);
    }

    private void setupDisplayConfigControls() {
        suppressDisplayConfigCallbacks = true;
        toggleHudMode.check(CarHudDisplayConfig.isHudMode(this) ? R.id.btnModeHud : R.id.btnModeNormal);
        toggleFlip.check(flipToButtonId(CarHudDisplayConfig.getFlip(this)));
        sliderBrightness.setValue(CarHudDisplayConfig.getBrightness(this));
        updateBrightnessValue(Math.round(sliderBrightness.getValue()));
        suppressDisplayConfigCallbacks = false;

        toggleHudMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!suppressDisplayConfigCallbacks && isChecked) {
                sendDisplayConfigFromUi();
            }
        });
        toggleFlip.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!suppressDisplayConfigCallbacks && isChecked) {
                sendDisplayConfigFromUi();
            }
        });
        sliderBrightness.addOnChangeListener((slider, value, fromUser) -> updateBrightnessValue(Math.round(value)));
        sliderBrightness.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                if (!suppressDisplayConfigCallbacks) {
                    sendDisplayConfigFromUi();
                }
            }
        });
        setDisplayConfigControlsEnabled(false);
    }

    private void sendDisplayConfigFromUi() {
        if (!bleReady) {
            Toast.makeText(this, "Can ket noi BLE ready truoc khi doi cai dat man hinh", Toast.LENGTH_SHORT).show();
            setDisplayConfigControlsEnabled(false);
            return;
        }

        boolean hudMode = toggleHudMode.getCheckedButtonId() == R.id.btnModeHud;
        String flip = buttonIdToFlip(toggleFlip.getCheckedButtonId());
        int brightness = Math.round(sliderBrightness.getValue());
        CarHudDisplayConfig.save(this, hudMode, flip, brightness);

        Intent intent = new Intent(this, CarHudService.class);
        intent.setAction(CarHudConstants.ACTION_SEND_DISPLAY_CONFIG);
        intent.putExtra(CarHudConstants.EXTRA_DISPLAY_MODE,
                hudMode ? CarHudDisplayConfig.MODE_HUD : CarHudDisplayConfig.MODE_NORMAL);
        intent.putExtra(CarHudConstants.EXTRA_DISPLAY_FLIP, flip);
        intent.putExtra(CarHudConstants.EXTRA_DISPLAY_BRIGHTNESS, brightness);
        startService(intent);
        Toast.makeText(this, "Da gui cau hinh man hinh", Toast.LENGTH_SHORT).show();
    }

    private void setDisplayConfigControlsEnabled(boolean enabled) {
        setToggleGroupEnabled(toggleHudMode, enabled);
        setToggleGroupEnabled(toggleFlip, enabled);
        sliderBrightness.setEnabled(enabled);
        txtDisplayConfigHint.setText(enabled
                ? "Display config: BLE ready"
                : "Display config: waiting for BLE ready");
    }

    private void setToggleGroupEnabled(MaterialButtonToggleGroup group, boolean enabled) {
        group.setEnabled(enabled);
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setEnabled(enabled);
        }
    }

    private int flipToButtonId(String flip) {
        if (CarHudDisplayConfig.FLIP_HORIZONTAL.equals(flip)) {
            return R.id.btnFlipHorizontal;
        }
        if (CarHudDisplayConfig.FLIP_ROTATE_180.equals(flip)) {
            return R.id.btnFlipRotate180;
        }
        if (CarHudDisplayConfig.FLIP_NONE.equals(flip)) {
            return R.id.btnFlipNone;
        }
        return R.id.btnFlipVertical;
    }

    private String buttonIdToFlip(int checkedButtonId) {
        if (checkedButtonId == R.id.btnFlipHorizontal) {
            return CarHudDisplayConfig.FLIP_HORIZONTAL;
        }
        if (checkedButtonId == R.id.btnFlipRotate180) {
            return CarHudDisplayConfig.FLIP_ROTATE_180;
        }
        if (checkedButtonId == R.id.btnFlipNone) {
            return CarHudDisplayConfig.FLIP_NONE;
        }
        return CarHudDisplayConfig.FLIP_VERTICAL;
    }

    private void updateBrightnessValue(int brightness) {
        txtBrightnessValue.setText(CarHudDisplayConfig.clampBrightness(brightness) + " / 255");
    }

    private void updatePermissionStatus() {
        List<String> missing = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appendMissingPermissionIfNeeded(Manifest.permission.BLUETOOTH_SCAN, "BLUETOOTH_SCAN", missing);
            appendMissingPermissionIfNeeded(Manifest.permission.BLUETOOTH_CONNECT, "BLUETOOTH_CONNECT", missing);
        }
        appendMissingPermissionIfNeeded(Manifest.permission.ACCESS_FINE_LOCATION, "ACCESS_FINE_LOCATION", missing);
        appendMissingPermissionIfNeeded(Manifest.permission.READ_PHONE_STATE, "READ_PHONE_STATE", missing);
        appendMissingPermissionIfNeeded(Manifest.permission.READ_CONTACTS, "READ_CONTACTS", missing);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appendMissingPermissionIfNeeded(Manifest.permission.POST_NOTIFICATIONS, "POST_NOTIFICATIONS", missing);
        }

        if (missing.isEmpty()) {
            txtPermissionStatus.setText("Runtime permissions: OK");
        } else {
            txtPermissionStatus.setText("Missing permissions: " + TextUtils.join(", ", missing));
        }
    }

    private void appendMissingPermissionIfNeeded(String permission, String label, List<String> missing) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(label);
        }
    }

    private void updateNotificationAccessStatus() {
        boolean enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
                .contains(getPackageName());
        txtNotificationAccessStatus.setText(enabled
                ? "Notification access: enabled"
                : "Notification access: disabled");
    }

    private void appendBleTimeline(String status) {
        if (status == null || status.trim().isEmpty()) {
            return;
        }
        String trimmed = status.trim();
        if (trimmed.equals(lastTimelineStatus)) {
            return;
        }
        lastTimelineStatus = trimmed;
        if (bleTimeline.length() > 0) {
            bleTimeline.insert(0, '\n');
        }
        bleTimeline.insert(0, trimmed);
        if (bleTimeline.length() > 1200) {
            bleTimeline.setLength(1200);
        }
        txtBleTimeline.setText(bleTimeline.toString());
    }

    private void applyBleStatus(String status, boolean connected, boolean ready, boolean running, boolean appendTimeline) {
        String safeStatus = TextUtils.isEmpty(status) ? "BLE: idle" : status;
        txtBleStatus.setText(safeStatus);
        bleReady = ready;
        hudRunning = running;
        if (safeStatus.toLowerCase().contains("stopped") || safeStatus.toLowerCase().contains("idle")) {
            hudRunning = false;
        }
        if (ready) {
            hudRunning = true;
        }
        if (bleReady) {
            txtBleConnectedStatus.setText("BLE ready: YES");
        } else {
            txtBleConnectedStatus.setText(connected ? "BLE connected: waiting for RX" : "BLE connected: NO");
        }
        updateConnectionUi(safeStatus, connected, ready);
        setDisplayConfigControlsEnabled(bleReady);
        if (appendTimeline) {
            appendBleTimeline(safeStatus);
        }
    }

    private void updateConnectionUi(String status, boolean connected, boolean ready) {
        String lower = status.toLowerCase();
        boolean stopping = lower.contains("stopping") || lower.contains("stop requested");
        boolean stopped = lower.contains("stopped") || lower.contains("idle");
        boolean working = !ready && (
                lower.contains("requested")
                        || lower.contains("starting")
                        || lower.contains("preparing bluetooth")
                        || lower.contains("scanning")
                        || lower.contains("try saved device")
                        || lower.contains("saved device timeout")
                        || lower.contains("connecting")
                        || lower.contains("requesting mtu")
                        || lower.contains("discovering services")
                        || lower.contains("services discovered")
                        || lower.contains("retrying")
        );

        if (ready) {
            txtBleChip.setText("CONNECTED");
            txtBleChip.setTextColor(ContextCompat.getColor(this, R.color.hud_green));
            txtConnectionProgress.setText("Ready: RX characteristic is available");
            connectionProgressBar.setVisibility(View.GONE);
            btnStartHud.setEnabled(false);
            btnStartHud.setText("Started");
            btnStopHud.setEnabled(hudRunning || connected);
            btnStopHud.setText("Stop");
            return;
        }

        if (stopping) {
            txtBleChip.setText("STOPPING");
            txtBleChip.setTextColor(ContextCompat.getColor(this, R.color.hud_amber));
            txtConnectionProgress.setText("Stopping BLE, speed updates and foreground service");
            connectionProgressBar.setVisibility(View.VISIBLE);
            btnStartHud.setEnabled(false);
            btnStartHud.setText("Start Service");
            btnStopHud.setEnabled(false);
            btnStopHud.setText("Stopping...");
            return;
        }

        if (working) {
            txtBleChip.setText(connected ? "CONNECTING" : "SCANNING");
            txtBleChip.setTextColor(ContextCompat.getColor(this, R.color.hud_amber));
            txtConnectionProgress.setText(toConnectionProgressText(lower));
            connectionProgressBar.setVisibility(View.VISIBLE);
            btnStartHud.setEnabled(false);
            btnStartHud.setText("Starting...");
            btnStopHud.setEnabled(true);
            btnStopHud.setText("Stop");
            return;
        }

        txtBleChip.setText(connected ? "CONNECTED" : "OFFLINE");
        txtBleChip.setTextColor(ContextCompat.getColor(
                this,
                connected ? R.color.hud_amber : R.color.text_muted
        ));
        txtConnectionProgress.setText(stopped ? "Service is stopped" : status);
        connectionProgressBar.setVisibility(View.GONE);
        btnStartHud.setEnabled(!connected);
        btnStartHud.setText("Start Service");
        btnStopHud.setEnabled(hudRunning || connected || !stopped);
        btnStopHud.setText("Stop");
    }

    private String toConnectionProgressText(String lowerStatus) {
        if (lowerStatus.contains("start requested") || lowerStatus.contains("starting")) {
            return "Starting foreground service";
        }
        if (lowerStatus.contains("preparing bluetooth")) {
            return "Preparing Bluetooth adapter and scanner";
        }
        if (lowerStatus.contains("scanning")) {
            return "Scanning for CarHUD-ESP32";
        }
        if (lowerStatus.contains("saved device timeout")) {
            return "Saved ESP32 did not respond, switching to scan";
        }
        if (lowerStatus.contains("try saved device")) {
            return "Trying saved ESP32 before scanning";
        }
        if (lowerStatus.contains("connecting")) {
            return "Connecting to saved or scanned ESP32";
        }
        if (lowerStatus.contains("requesting mtu")) {
            return "Connected: requesting MTU 247";
        }
        if (lowerStatus.contains("discovering services")) {
            return "Discovering BLE service and RX characteristic";
        }
        if (lowerStatus.contains("services discovered")) {
            return "Checking service and RX characteristic";
        }
        if (lowerStatus.contains("retrying")) {
            return "Disconnected: retrying connection";
        }
        return "Updating BLE connection state";
    }

    private boolean inferReady(String status) {
        return status != null && status.toLowerCase().startsWith("ble: ready");
    }
}
