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
            // Fallback to scan if direct reconnect cannot establish quickly.
            mainHandler.postDelayed(() -> {
                if (started && gatt == null && !isScanning) {
                    startScanInternal();
                }
            }, 3500L);
            return;
        }

        startScanInternal();
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        started = false;
        stopScanInternal();

        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }

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

        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        isScanning = true;
        notifyStatus("BLE: scanning CarHUD-ESP32");
    }

    @SuppressLint("MissingPermission")
    private void stopScanInternal() {
        if (!isScanning || scanner == null) {
            return;
        }
        scanner.stopScan(scanCallback);
        isScanning = false;
    }

    @SuppressLint("MissingPermission")
    private void connect(BluetoothDevice device) {
        stopScanInternal();
        if (gatt != null) {
            gatt.close();
            gatt = null;
        }
        notifyStatus("BLE: connecting " + (device.getName() == null ? "device" : device.getName()));
        connectedDevice = device;
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    private void notifyStatus(String text) {
        statusListener.onStatusChanged(text);
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
                mainHandler.postDelayed(() -> {
                    if (started && gatt == null && !isScanning) {
                        startScanInternal();
                    }
                }, 3500L);
                return;
            }
            startScanInternal();
        }, 2000L);
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
            if (device.getName() != null && device.getName().equals(CarHudConstants.DEVICE_NAME)) {
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
                saveDeviceMac(g.getDevice() == null ? null : g.getDevice().getAddress());
                notifyStatus("BLE: connected, requesting MTU 247");
                g.requestMtu(247);
                return;
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                notifyStatus("BLE: disconnected, retrying");
                rxCharacteristic = null;
                writeInFlight = false;
                if (gatt != null) {
                    gatt.close();
                    gatt = null;
                }
                scheduleReconnect();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            notifyStatus("BLE: MTU=" + mtu + ", discovering services");
            g.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService service = g.getService(SERVICE_UUID);
            if (service == null) {
                notifyStatus("BLE: service not found");
                return;
            }
            rxCharacteristic = service.getCharacteristic(RX_UUID);
            if (rxCharacteristic == null) {
                notifyStatus("BLE: RX characteristic not found");
                return;
            }
            notifyStatus("BLE: ready");
            flushQueue();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {
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
