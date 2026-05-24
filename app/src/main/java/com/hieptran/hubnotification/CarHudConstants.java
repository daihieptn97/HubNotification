package com.hieptran.hubnotification;

public final class CarHudConstants {
    private CarHudConstants() {
    }

    public static final String DEVICE_NAME = "CarHUD-ESP32";

    public static final String SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String CHAR_RX_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String CHAR_TX_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

    public static final String ACTION_START_HUD = "com.hieptran.hubnotification.action.START_HUD";
    public static final String ACTION_STOP_HUD = "com.hieptran.hubnotification.action.STOP_HUD";
    public static final String ACTION_SEND_TEST_NAV = "com.hieptran.hubnotification.action.SEND_TEST_NAV";
    public static final String ACTION_SEND_TEST_PAYLOAD = "com.hieptran.hubnotification.action.SEND_TEST_PAYLOAD";

    public static final String ACTION_STATUS_UPDATE = "com.hieptran.hubnotification.action.STATUS_UPDATE";
    public static final String EXTRA_BLE_STATUS = "extra_ble_status";
    public static final String EXTRA_BLE_CONNECTED = "extra_ble_connected";
    public static final String EXTRA_TEST_PAYLOAD = "extra_test_payload";
}
