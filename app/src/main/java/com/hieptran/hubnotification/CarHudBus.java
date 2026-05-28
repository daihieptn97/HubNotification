package com.hieptran.hubnotification;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class CarHudBus {
    private static volatile BleClient bleClient;

    private CarHudBus() {
    }

    public static void init(BleClient client) {
        bleClient = client;
    }

    public static void publishNav(GoogleMapsNavParser.NavInfo navInfo) {
        if (navInfo == null) {
            return;
        }
        JSONObject json = new JSONObject();
        try {
            json.put("t", "nav");
            json.put("arr", navInfo.arrow);
            json.put("d", navInfo.distance);
            json.put("u", navInfo.unit);
            json.put("s", CarHudTextUtils.limit(CarHudTextUtils.stripVietnameseDiacritics(navInfo.street), 47));
            sendRaw(json.toString());
        } catch (JSONException ignored) {
        }
    }

    public static void publishSpeed(int kmh) {
        sendRaw("{\"t\":\"spd\",\"v\":" + Math.max(kmh, 0) + "}");
    }

    public static void publishCall(@Nullable String name, @Nullable String phone) {
        JSONObject json = new JSONObject();
        try {
            json.put("t", "call");
            json.put("n", CarHudTextUtils.limit(CarHudTextUtils.stripVietnameseDiacritics(name), 31));
            json.put("p", CarHudTextUtils.limit(phone == null ? "" : phone.trim(), 23));
            sendRaw(json.toString());
        } catch (JSONException ignored) {
        }
    }

    public static void publishSms(@Nullable String from, @Nullable String message) {
        JSONObject json = new JSONObject();
        try {
            json.put("t", "sms");
            json.put("f", CarHudTextUtils.limit(CarHudTextUtils.stripVietnameseDiacritics(from), 31));
            json.put("m", CarHudTextUtils.limit(CarHudTextUtils.stripVietnameseDiacritics(message), 127));
            sendRaw(json.toString());
        } catch (JSONException ignored) {
        }
    }

    public static void publishClock(int hour, int minute) {
        sendRaw("{\"t\":\"clk\",\"h\":" + hour + ",\"m\":" + minute + "}");
    }

    public static void publishBattery(int batteryPct) {
        int pct = Math.max(0, Math.min(100, batteryPct));
        sendRaw("{\"t\":\"bat\",\"p\":" + pct + "}");
    }

    public static void publishDisplayConfig(boolean hudMode, String flip, int brightness) {
        JSONObject json = new JSONObject();
        try {
            json.put("t", "cfg");
            json.put("mode", hudMode ? CarHudDisplayConfig.MODE_HUD : CarHudDisplayConfig.MODE_NORMAL);
            json.put("flip", CarHudDisplayConfig.sanitizeFlip(flip));
            json.put("br", CarHudDisplayConfig.clampBrightness(brightness));
            json.put("save", true);
            sendRaw(json.toString());
        } catch (JSONException ignored) {
        }
    }

    public static void publishClear() {
        sendRaw("{\"t\":\"clr\"}");
    }

    public static void sendRaw(String json) {
        BleClient client = bleClient;
        if (client != null) {
            client.send(json);
        }
    }
}
