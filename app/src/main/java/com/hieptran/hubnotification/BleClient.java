package com.hieptran.hubnotification;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.UUID;

public class BleClient {
    public interface StatusListener {
        void onStatusChanged(String message);
    }

    private static final String TAG = "BleClient";
    private static final String PREF_BLE = "car_hud_ble";
    private static final String KEY_LAST_MAC = "last_mac";
    private static final long DIRECT_CONNECT_TIMEOUT_MS = 3_500L;

    private static final UUID SERVICE_UUID = UUID.fromString(CarHudConstants.SERVICE_UUID);
    private static final UUID RX_UUID = UUID.fromString(CarHudConstants.CHAR_RX_UUID);

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Queue<byte[]> txQueue = new ArrayDeque<>();
    private final StatusListener statusListener;

    private BluetoothLeScanner scanner;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothDevice connectedDevice;

    private boolean isScanning;
    private boolean writeInFlight;
    private boolean started;

    public BleClient(@NonNull Context context, @NonNull StatusListener listener) {
        this.appContext = context.getApplicationContext();
        this.statusListener = listener;
    }

    @SuppressLint("MissingPermission")
    public void start() {
        started = true;
        notifyStatus("BLE: preparing Bluetooth");
        if (!hasBluetoothRuntimePermission()) {
            notifyStatus("BLE: missing BLUETOOTH permissions");
            return;
        }

        BluetoothManager manager = appContext.getSystemService(BluetoothManager.class);
        if (manager == null) {
            notifyStatus("BLE: BluetoothManager unavailable");
            return;
        }
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            notifyStatus("BLE: adapter disabled");
            return;
        }
        bluetoothAdapter = adapter;

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            notifyStatus("BLE: scanner unavailable");
            return;
        }

        if (tryConnectSavedDevice(adapter)) {
            scheduleConnectFallback("saved device timeout");
            return;
        }

        startScanInternal();
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        started = false;
        notifyStatus("BLE: stopping BLE client");
        stopScanInternal();
        closeGatt();

        rxCharacteristic = null;
        connectedDevice = null;
        bluetoothAdapter = null;
        txQueue.clear();
        writeInFlight = false;
        notifyStatus("BLE: stopped");
    }

    public void send(String json) {
        if (json == null) {
            return;
        }

        Log.d(TAG, "TX " + json);
        NotificationDebugLogStore.appendTx(appContext, json);

        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        if (data.length > 240) {
            Log.w(TAG, "Payload too large: " + data.length);
            return;
        }
        txQueue.offer(data);
        flushQueue();
    }

    private boolean hasBluetoothRuntimePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void startScanInternal() {
        if (!started || scanner == null || isScanning) {
            return;
        }
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try {
            scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
            isScanning = true;
            notifyStatus("BLE: scanning CarHUD-ESP32");
        } catch (SecurityException ex) {
            notifyStatus("BLE: scan failed, missing permission");
        } catch (IllegalStateException ex) {
            notifyStatus("BLE: scan failed");
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScanInternal() {
        if (!isScanning || scanner == null) {
            return;
        }
        try {
            scanner.stopScan(scanCallback);
        } catch (SecurityException ex) {
            notifyStatus("BLE: stop scan failed, missing permission");
        } catch (IllegalStateException ex) {
            notifyStatus("BLE: stop scan failed");
        }
        isScanning = false;
    }

    @SuppressLint("MissingPermission")
    private void connect(BluetoothDevice device) {
        stopScanInternal();
        closeGatt();
        notifyStatus("BLE: connecting " + getDeviceLabel(device));
        connectedDevice = device;
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        if (gatt == null) {
            connectedDevice = null;
            notifyStatus("BLE: connectGatt failed, scanning");
            startScanInternal();
        } else {
            scheduleConnectFallback("connect timeout");
        }
    }

    private void notifyStatus(String text) {
        Log.d(TAG, "STATUS " + text);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            statusListener.onStatusChanged(text);
        } else {
            mainHandler.post(() -> statusListener.onStatusChanged(text));
        }
    }

    private void scheduleReconnect() {
        if (!started) {
            return;
        }
        mainHandler.postDelayed(() -> {
            if (!started) {
                return;
            }
            if (bluetoothAdapter != null && tryConnectSavedDevice(bluetoothAdapter)) {
                scheduleConnectFallback("saved device retry timeout");
                return;
            }
            startScanInternal();
        }, 2000L);
    }

    private void scheduleConnectFallback(String reason) {
        mainHandler.postDelayed(() -> {
            if (!started || rxCharacteristic != null || isScanning) {
                return;
            }
            notifyStatus("BLE: " + reason + ", scanning");
            closeGatt();
            startScanInternal();
        }, DIRECT_CONNECT_TIMEOUT_MS);
    }

    @SuppressLint("MissingPermission")
    private void recoverToScan(String reason) {
        if (!started) {
            return;
        }
        notifyStatus("BLE: " + reason + ", scanning");
        closeGatt();
        startScanInternal();
    }

    @SuppressLint("MissingPermission")
    private void closeGatt() {
        BluetoothGatt localGatt = gatt;
        gatt = null;
        rxCharacteristic = null;
        writeInFlight = false;
        if (localGatt == null) {
            return;
        }
        try {
            localGatt.disconnect();
        } catch (SecurityException ignored) {
        }
        try {
            localGatt.close();
        } catch (Exception ignored) {
        }
    }

    @SuppressLint("MissingPermission")
    private String getDeviceLabel(BluetoothDevice device) {
        if (device == null) {
            return "device";
        }
        try {
            String name = device.getName();
            return TextUtils.isEmpty(name) ? device.getAddress() : name;
        } catch (SecurityException ex) {
            return "device";
        }
    }

    @SuppressLint("MissingPermission")
    private boolean tryConnectSavedDevice(BluetoothAdapter adapter) {
        String savedMac = getSavedDeviceMac();
        if (TextUtils.isEmpty(savedMac)) {
            return false;
        }

        try {
            BluetoothDevice device = adapter.getRemoteDevice(savedMac);
            notifyStatus("BLE: try saved device " + savedMac);
            connect(device);
            return true;
        } catch (IllegalArgumentException ex) {
            clearSavedDeviceMac();
            notifyStatus("BLE: invalid saved MAC, fallback scan");
            return false;
        }
    }

    private void saveDeviceMac(String mac) {
        if (TextUtils.isEmpty(mac)) {
            return;
        }
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_BLE, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_MAC, mac).apply();
    }

    private void clearSavedDeviceMac() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_BLE, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_LAST_MAC).apply();
    }

    private String getSavedDeviceMac() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_BLE, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_MAC, null);
    }

    @SuppressLint("MissingPermission")
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device == null) {
                return;
            }
            String advertisedName = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
            if (CarHudConstants.DEVICE_NAME.equals(getDeviceLabel(device))
                    || CarHudConstants.DEVICE_NAME.equals(advertisedName)) {
                stopScanInternal();
                connect(device);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            notifyStatus("BLE: scan failed " + errorCode);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!started) {
                    return;
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    notifyStatus("BLE: connect failed " + status + ", retrying");
                    closeGatt();
                    scheduleReconnect();
                    return;
                }
                saveDeviceMac(g.getDevice() == null ? null : g.getDevice().getAddress());
                notifyStatus("BLE: connected, requesting MTU 247");
                if (!g.requestMtu(247)) {
                    notifyStatus("BLE: MTU request failed, discovering services");
                    g.discoverServices();
                }
                return;
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (!started) {
                    return;
                }
                notifyStatus("BLE: disconnected, retrying");
                rxCharacteristic = null;
                writeInFlight = false;
                closeGatt();
                scheduleReconnect();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            if (!started) {
                return;
            }
            notifyStatus("BLE: MTU=" + mtu + ", discovering services");
            if (!g.discoverServices()) {
                recoverToScan("service discovery failed");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (!started) {
                return;
            }
            notifyStatus("BLE: services discovered status=" + status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                recoverToScan("service discovery failed " + status);
                return;
            }
            BluetoothGattService service = g.getService(SERVICE_UUID);
            if (service == null) {
                recoverToScan("service not found");
                return;
            }
            rxCharacteristic = service.getCharacteristic(RX_UUID);
            if (rxCharacteristic == null) {
                recoverToScan("RX characteristic not found");
                return;
            }
            notifyStatus("BLE: ready");
            flushQueue();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {
            if (!started) {
                return;
            }
            writeInFlight = false;
            flushQueue();
        }
    };

    @SuppressLint("MissingPermission")
    private void flushQueue() {
        BluetoothGatt localGatt = gatt;
        BluetoothGattCharacteristic localRx = rxCharacteristic;

        if (writeInFlight || localGatt == null || localRx == null) {
            return;
        }

        byte[] next = txQueue.poll();
        if (next == null) {
            return;
        }

        writeInFlight = true;

        boolean writeStarted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeStarted = localGatt.writeCharacteristic(
                    localRx,
                    next,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) == BluetoothGatt.GATT_SUCCESS;
        } else {
            localRx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            localRx.setValue(next);
            writeStarted = localGatt.writeCharacteristic(localRx);
        }

        if (!writeStarted) {
            writeInFlight = false;
            notifyStatus("BLE: write failed to start");
            return;
        }

        mainHandler.postDelayed(() -> {
            if (writeInFlight) {
                writeInFlight = false;
                flushQueue();
            }
        }, 120L);
    }
}
