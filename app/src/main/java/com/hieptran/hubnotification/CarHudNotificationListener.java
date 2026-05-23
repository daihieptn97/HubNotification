package com.hieptran.hubnotification;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.annotation.Nullable;

public class CarHudNotificationListener extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }

        String packageName = sbn.getPackageName();
        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        if ("com.google.android.apps.maps".equals(packageName)) {
            handleMapsNotification(sbn);
            return;
        }

        if (isDialerPackage(packageName)) {
            handleCallNotification(sbn);
            return;
        }

        if (isMessagePackage(packageName)) {
            handleMessageNotification(sbn);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName() == null) {
            return;
        }
        if (isDialerPackage(sbn.getPackageName())) {
            CarHudBus.publishClear();
        }
    }

    private void handleMapsNotification(StatusBarNotification sbn) {
        Bundle extras = sbn.getNotification().extras;
        String title = getString(extras, Notification.EXTRA_TITLE);
        String text = getString(extras, Notification.EXTRA_TEXT);
        String subText = getString(extras, Notification.EXTRA_SUB_TEXT);

        GoogleMapsNavParser.NavInfo navInfo = GoogleMapsNavParser.parse(title, text, subText);
        if (navInfo != null) {
            CarHudBus.publishNav(navInfo);
        }
    }

    private void handleCallNotification(StatusBarNotification sbn) {
        Bundle extras = sbn.getNotification().extras;
        String title = getString(extras, Notification.EXTRA_TITLE);
        String text = getString(extras, Notification.EXTRA_TEXT);
        String person = getString(extras, Notification.EXTRA_SUB_TEXT);

        String name = !TextUtils.isEmpty(title) ? title : person;
        if (TextUtils.isEmpty(name)) {
            name = "Unknown";
        }
        String phone = !TextUtils.isEmpty(text) ? text : "";

        CarHudBus.publishCall(name, phone);
    }

    private void handleMessageNotification(StatusBarNotification sbn) {
        Bundle extras = sbn.getNotification().extras;
        String sender = getString(extras, Notification.EXTRA_TITLE);
        String body = getString(extras, Notification.EXTRA_TEXT);

        if (TextUtils.isEmpty(sender) && TextUtils.isEmpty(body)) {
            return;
        }
        if (TextUtils.isEmpty(sender)) {
            sender = "Unknown";
        }
        CarHudBus.publishSms(sender, body == null ? "" : body);
    }

    private boolean isDialerPackage(String packageName) {
        return "com.google.android.dialer".equals(packageName)
                || "com.android.phone".equals(packageName)
                || "com.samsung.android.dialer".equals(packageName)
                || "com.android.server.telecom".equals(packageName);
    }

    private boolean isMessagePackage(String packageName) {
        return "com.google.android.apps.messaging".equals(packageName)
                || "org.telegram.messenger".equals(packageName)
                || "com.zing.zalo".equals(packageName)
                || "com.facebook.orca".equals(packageName);
    }

    @Nullable
    private String getString(Bundle extras, String key) {
        if (extras == null) {
            return null;
        }
        CharSequence value = extras.getCharSequence(key);
        if (value != null) {
            return value.toString();
        }
        return extras.getString(key);
    }
}
