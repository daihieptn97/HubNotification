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

        Bundle extras = sbn.getNotification().extras;
        String rawTitle = firstNonBlank(
                getString(extras, Notification.EXTRA_TITLE),
                getString(extras, Notification.EXTRA_TITLE_BIG)
        );
        String rawText = firstNonBlank(
                getString(extras, Notification.EXTRA_TEXT),
                getString(extras, Notification.EXTRA_BIG_TEXT),
                getTextLines(extras)
        );
        String rawSubText = firstNonBlank(
                getString(extras, Notification.EXTRA_SUB_TEXT),
                getString(extras, Notification.EXTRA_SUMMARY_TEXT)
        );

        if (!NotificationAppConfig.isPackageEnabled(this, packageName)) {
            NotificationDebugLogStore.append(this, packageName, rawTitle, rawText, rawSubText, "ignored_disabled");
            return;
        }

        if ("com.google.android.apps.maps".equals(packageName)) {
            handleMapsNotification(rawTitle, rawText, rawSubText);
            NotificationDebugLogStore.append(this, packageName, rawTitle, rawText, rawSubText, "handled_maps");
            return;
        }

        if (isDialerPackage(packageName)) {
            handleCallNotification(rawTitle, rawText, rawSubText);
            NotificationDebugLogStore.append(this, packageName, rawTitle, rawText, rawSubText, "handled_call");
            return;
        }

        if (isMessagePackage(packageName)) {
            handleMessageNotification(rawTitle, rawText);
            NotificationDebugLogStore.append(this, packageName, rawTitle, rawText, rawSubText, "handled_message");
            return;
        }

        NotificationDebugLogStore.append(this, packageName, rawTitle, rawText, rawSubText, "ignored_unmapped");
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

        private void handleMapsNotification(String title, String text, String subText) {
        GoogleMapsNavParser.NavInfo navInfo = GoogleMapsNavParser.parse(title, text, subText);
        if (navInfo != null) {
            CarHudBus.publishNav(navInfo);
        }
    }

        private void handleCallNotification(String title, String text, String person) {

        String name = !TextUtils.isEmpty(title) ? title : person;
        if (TextUtils.isEmpty(name)) {
            name = "Unknown";
        }
        String phone = !TextUtils.isEmpty(text) ? text : "";

        CarHudBus.publishCall(name, phone);
    }

        private void handleMessageNotification(String sender, String body) {

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
                || "com.facebook.orca".equals(packageName)
                || "com.whatsapp".equals(packageName);
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

    @Nullable
    private String getTextLines(Bundle extras) {
        if (extras == null) {
            return null;
        }
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines == null || lines.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (CharSequence line : lines) {
            if (line == null || line.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(line);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    @Nullable
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
