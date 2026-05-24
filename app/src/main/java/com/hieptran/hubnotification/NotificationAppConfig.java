package com.hieptran.hubnotification;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

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
        Map<String, AppEntry> appMap = new LinkedHashMap<>();

        addLauncherApps(packageManager, context, appMap);
        addDefaultsAndSavedPackages(context, packageManager, appMap);
        addInstalledAppsBestEffort(packageManager, context, appMap);

        List<AppEntry> apps = new ArrayList<>(appMap.values());
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));
        return apps;
    }

    private static void addLauncherApps(PackageManager packageManager, Context context, Map<String, AppEntry> appMap) {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchables;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launchables = packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0)
            );
        } else {
            launchables = packageManager.queryIntentActivities(launcherIntent, 0);
        }

        for (ResolveInfo info : launchables) {
            if (info.activityInfo == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            addPackageEntry(packageManager, context, appMap, packageName);
        }
    }

    private static void addDefaultsAndSavedPackages(Context context, PackageManager packageManager, Map<String, AppEntry> appMap) {
        for (String packageName : DEFAULT_STATE.keySet()) {
            addPackageEntry(packageManager, context, appMap, packageName);
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        for (String packageName : prefs.getAll().keySet()) {
            addPackageEntry(packageManager, context, appMap, packageName);
        }
    }

    private static void addInstalledAppsBestEffort(PackageManager packageManager, Context context, Map<String, AppEntry> appMap) {
        try {
            List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo appInfo : installedApps) {
                addPackageEntry(packageManager, context, appMap, appInfo.packageName);
            }
        } catch (Exception ignored) {
            // Some devices/API levels can restrict full package visibility.
        }
    }

    private static void addPackageEntry(PackageManager packageManager, Context context, Map<String, AppEntry> appMap, String packageName) {
        if (packageName == null || context.getPackageName().equals(packageName) || appMap.containsKey(packageName)) {
            return;
        }

        String label = packageName;
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence appLabel = packageManager.getApplicationLabel(appInfo);
            if (appLabel != null && appLabel.length() > 0) {
                label = appLabel.toString();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        appMap.put(packageName, new AppEntry(label, packageName));
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
