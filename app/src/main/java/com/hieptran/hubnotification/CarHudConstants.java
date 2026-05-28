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
    public static final String ACTION_SEND_DISPLAY_CONFIG = "com.hieptran.hubnotification.action.SEND_DISPLAY_CONFIG";
    public static final String ACTION_REQUEST_STATUS = "com.hieptran.hubnotification.action.REQUEST_STATUS";

    public static final String ACTION_STATUS_UPDATE = "com.hieptran.hubnotification.action.STATUS_UPDATE";
    public static final String EXTRA_BLE_STATUS = "extra_ble_status";
    public static final String EXTRA_BLE_CONNECTED = "extra_ble_connected";
    public static final String EXTRA_BLE_READY = "extra_ble_ready";
    public static final String EXTRA_HUD_RUNNING = "extra_hud_running";
    public static final String EXTRA_TEST_PAYLOAD = "extra_test_payload";
    public static final String EXTRA_DISPLAY_MODE = "extra_display_mode";
    public static final String EXTRA_DISPLAY_FLIP = "extra_display_flip";
    public static final String EXTRA_DISPLAY_BRIGHTNESS = "extra_display_brightness";
}
