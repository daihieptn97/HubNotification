package com.hieptran.hubnotification;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

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

public class MainActivity extends AppCompatActivity {
    private TextView txtPermissionStatus;
    private TextView txtNotificationAccessStatus;
    private TextView txtBleConnectedStatus;
    private TextView txtBleStatus;
    private TextView txtBleTimeline;
    private final StringBuilder bleTimeline = new StringBuilder();

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
                txtBleStatus.setText(bleStatus);
                boolean connected = intent.getBooleanExtra(CarHudConstants.EXTRA_BLE_CONNECTED, false);
                txtBleConnectedStatus.setText(connected ? "BLE connected: YES" : "BLE connected: NO");
                appendBleTimeline(bleStatus);
            }
        }
    };

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
        txtBleConnectedStatus = findViewById(R.id.txtBleConnectedStatus);
        txtBleStatus = findViewById(R.id.txtBleStatus);
        txtBleTimeline = findViewById(R.id.txtBleTimeline);
        Button btnRequestPermissions = findViewById(R.id.btnRequestPermissions);
        Button btnOpenNotificationAccess = findViewById(R.id.btnOpenNotificationAccess);
        Button btnManageNotificationApps = findViewById(R.id.btnManageNotificationApps);
        Button btnOpenDebugLogs = findViewById(R.id.btnOpenDebugLogs);
        Button btnOpenLogManager = findViewById(R.id.btnOpenLogManager);
        Button btnOpenNavTest = findViewById(R.id.btnOpenNavTest);
        Button btnStartHud = findViewById(R.id.btnStartHud);
        Button btnStopHud = findViewById(R.id.btnStopHud);
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

        updatePermissionStatus();
        updateNotificationAccessStatus();
        txtBleConnectedStatus.setText("BLE connected: NO");
        txtBleStatus.setText("BLE: idle");
        txtBleTimeline.setText("No BLE events yet.");
    }

    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(
                this,
                statusReceiver,
                new IntentFilter(CarHudConstants.ACTION_STATUS_UPDATE),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(statusReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
        updateNotificationAccessStatus();
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
        Intent intent = new Intent(this, CarHudService.class);
        intent.setAction(CarHudConstants.ACTION_START_HUD);
        ContextCompat.startForegroundService(this, intent);
    }

    private void stopHudService() {
        Intent intent = new Intent(this, CarHudService.class);
        intent.setAction(CarHudConstants.ACTION_STOP_HUD);
        startService(intent);
    }

    private void sendTestNav() {
        Intent intent = new Intent(this, CarHudService.class);
        intent.setAction(CarHudConstants.ACTION_SEND_TEST_NAV);
        startService(intent);
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
        if (bleTimeline.length() > 0) {
            bleTimeline.insert(0, '\n');
        }
        bleTimeline.insert(0, status.trim());
        if (bleTimeline.length() > 1200) {
            bleTimeline.setLength(1200);
        }
        txtBleTimeline.setText(bleTimeline.toString());
    }
}