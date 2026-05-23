package com.hieptran.hubnotification;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NotificationDebugLogStore {
    private static final String PREFS_NAME = "car_hud_debug";
    private static final String KEY_LOGS = "notification_logs";
    private static final int MAX_LOG_CHARS = 80_000;

    private NotificationDebugLogStore() {
    }

    public static synchronized void append(
            @NonNull Context context,
            @NonNull String packageName,
            @Nullable String title,
            @Nullable String text,
            @Nullable String subText,
            @NonNull String decision
    ) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String oldLogs = prefs.getString(KEY_LOGS, "");

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String entry = "[" + timestamp + "] " + packageName + " [" + decision + "]\n"
                + "  title: " + safe(title) + "\n"
                + "  text : " + safe(text) + "\n"
                + "  sub  : " + safe(subText) + "\n"
                + "----------------------------------------\n";

        String merged = entry + oldLogs;
        if (merged.length() > MAX_LOG_CHARS) {
            merged = merged.substring(0, MAX_LOG_CHARS);
        }

        prefs.edit().putString(KEY_LOGS, merged).apply();
    }

    @NonNull
    public static synchronized String read(@NonNull Context context) {
        return context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LOGS, "No debug logs yet.");
    }

    public static synchronized void clear(@NonNull Context context) {
        context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LOGS)
                .apply();
    }

    private static String safe(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return "<empty>";
        }
        return value.trim();
    }
}
