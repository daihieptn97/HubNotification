package com.hieptran.hubnotification;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NotificationAppConfig {
    private static final String PREFS_NAME = "car_hud_notification_apps";

    private static final LinkedHashMap<String, Boolean> DEFAULT_STATE = new LinkedHashMap<>();

    public static final class AppEntry {
        public final String label;
        public final String packageName;

        public AppEntry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    static {
        addDefaultEnabled("com.google.android.apps.maps");
        addDefaultEnabled("com.google.android.dialer");
        addDefaultEnabled("com.android.phone");
        addDefaultEnabled("com.samsung.android.dialer");
        addDefaultEnabled("com.google.android.apps.messaging");
        addDefaultEnabled("org.telegram.messenger");
        addDefaultEnabled("com.zing.zalo");
        addDefaultEnabled("com.facebook.orca");
        addDefaultEnabled("com.whatsapp");
    }

    private NotificationAppConfig() {
    }

    private static void addDefaultEnabled(String packageName) {
        DEFAULT_STATE.put(packageName, true);
    }

    @NonNull
    public static List<AppEntry> getInstalledApps(@NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        List<AppEntry> apps = new ArrayList<>();

        for (ApplicationInfo appInfo : installedApps) {
            String packageName = appInfo.packageName;
            if (context.getPackageName().equals(packageName)) {
                continue;
            }

            CharSequence appLabel = packageManager.getApplicationLabel(appInfo);
            String label = appLabel == null ? packageName : appLabel.toString();
            apps.add(new AppEntry(label, packageName));
        }

        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));
        return apps;
    }

    public static boolean isPackageEnabled(@NonNull Context context, @NonNull String packageName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(packageName)) {
            Boolean defaultValue = DEFAULT_STATE.get(packageName);
            return defaultValue != null && defaultValue;
        }
        return prefs.getBoolean(packageName, false);
    }

    public static void setPackageEnabled(@NonNull Context context, @NonNull String packageName, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(packageName, enabled)
                .apply();
    }
}
