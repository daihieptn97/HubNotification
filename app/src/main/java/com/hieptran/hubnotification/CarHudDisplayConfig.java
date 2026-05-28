package com.hieptran.hubnotification;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

public final class CarHudDisplayConfig {
    public static final String MODE_NORMAL = "normal";
    public static final String MODE_HUD = "hud";

    public static final String FLIP_VERTICAL = "v";
    public static final String FLIP_HORIZONTAL = "h";
    public static final String FLIP_ROTATE_180 = "r180";
    public static final String FLIP_NONE = "none";

    private static final String PREFS_NAME = "car_hud_display_config";
    private static final String KEY_MODE = "mode";
    private static final String KEY_FLIP = "flip";
    private static final String KEY_BRIGHTNESS = "brightness";

    private static final int DEFAULT_BRIGHTNESS = 255;

    private CarHudDisplayConfig() {
    }

    @NonNull
    public static String getMode(@NonNull Context context) {
        String mode = prefs(context).getString(KEY_MODE, MODE_NORMAL);
        return MODE_HUD.equals(mode) ? MODE_HUD : MODE_NORMAL;
    }

    public static boolean isHudMode(@NonNull Context context) {
        return MODE_HUD.equals(getMode(context));
    }

    @NonNull
    public static String getFlip(@NonNull Context context) {
        return sanitizeFlip(prefs(context).getString(KEY_FLIP, FLIP_VERTICAL));
    }

    public static int getBrightness(@NonNull Context context) {
        return clampBrightness(prefs(context).getInt(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS));
    }

    public static void save(@NonNull Context context, boolean hudMode, @NonNull String flip, int brightness) {
        prefs(context)
                .edit()
                .putString(KEY_MODE, hudMode ? MODE_HUD : MODE_NORMAL)
                .putString(KEY_FLIP, sanitizeFlip(flip))
                .putInt(KEY_BRIGHTNESS, clampBrightness(brightness))
                .apply();
    }

    @NonNull
    public static String sanitizeFlip(String flip) {
        if (FLIP_HORIZONTAL.equals(flip)
                || FLIP_ROTATE_180.equals(flip)
                || FLIP_NONE.equals(flip)) {
            return flip;
        }
        return FLIP_VERTICAL;
    }

    public static int clampBrightness(int brightness) {
        return Math.max(0, Math.min(255, brightness));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
