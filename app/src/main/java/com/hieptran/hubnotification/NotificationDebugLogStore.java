package com.hieptran.hubnotification;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NotificationDebugLogStore {
    private static final String PREFS_NAME = "car_hud_debug";
    private static final String KEY_LOGS = "notification_logs";
    private static final String KEY_LAST_TX = "last_tx_payload";
    private static final String KEY_TX_HISTORY = "tx_history";
    private static final int MAX_LOG_CHARS = 80_000;
    private static final int MAX_TX_HISTORY = 40;

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
                .remove(KEY_LAST_TX)
                .remove(KEY_TX_HISTORY)
                .apply();
    }

    public static synchronized void appendTx(@NonNull Context context, @NonNull String payload) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String oldLogs = prefs.getString(KEY_LOGS, "");

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String normalized = payload.replace('\n', ' ').trim();
        String entry = "[" + timestamp + "] TX [hud_send]\n"
                + "  json : " + safe(normalized) + "\n"
                + "----------------------------------------\n";

        String merged = entry + oldLogs;
        if (merged.length() > MAX_LOG_CHARS) {
            merged = merged.substring(0, MAX_LOG_CHARS);
        }

        String oldTxHistory = prefs.getString(KEY_TX_HISTORY, "");
        String mergedTxHistory = normalized + "\n" + oldTxHistory;
        String[] lines = mergedTxHistory.split("\n");
        StringBuilder txBuilder = new StringBuilder();
        int added = 0;
        for (String line : lines) {
            String item = line == null ? "" : line.trim();
            if (item.isEmpty()) {
                continue;
            }
            if (txBuilder.length() > 0) {
                txBuilder.append('\n');
            }
            txBuilder.append(item);
            added++;
            if (added >= MAX_TX_HISTORY) {
                break;
            }
        }

        prefs.edit()
                .putString(KEY_LAST_TX, normalized)
                .putString(KEY_LOGS, merged)
                .putString(KEY_TX_HISTORY, txBuilder.toString())
                .apply();
    }

    @NonNull
    public static synchronized String getLastTxPayload(@NonNull Context context) {
        return context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_TX, "");
    }

    @NonNull
    public static synchronized List<String> getLogEntries(@NonNull Context context) {
        String raw = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LOGS, "");

        List<String> entries = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty() || "No debug logs yet.".equals(raw.trim())) {
            return entries;
        }

        String[] chunks = raw.split("----------------------------------------\\n");
        for (String chunk : chunks) {
            String entry = chunk == null ? "" : chunk.trim();
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries;
    }

    @NonNull
    public static synchronized List<String> getTxHistory(@NonNull Context context) {
        String raw = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TX_HISTORY, "");

        List<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }

        String[] lines = raw.split("\n");
        for (String line : lines) {
            String item = line == null ? "" : line.trim();
            if (!item.isEmpty()) {
                result.add(item);
            }
        }
        return result;
    }

    @NonNull
    public static synchronized String buildAnalysisReport(@NonNull Context context) {
        List<String> entries = getLogEntries(context);
        List<String> tx = getTxHistory(context);
        Map<String, Integer> decisionCounts = new LinkedHashMap<>();
        Map<String, Integer> packageCounts = new LinkedHashMap<>();

        for (String entry : entries) {
            String decision = extractBetween(entry, "[", "]", 2);
            if (decision != null && !decision.isEmpty()) {
                decisionCounts.put(decision, decisionCounts.getOrDefault(decision, 0) + 1);
            }

            String firstLine = firstLine(entry);
            String pkg = extractPackage(firstLine);
            if (pkg != null && !pkg.isEmpty()) {
                packageCounts.put(pkg, packageCounts.getOrDefault(pkg, 0) + 1);
            }
        }

        StringBuilder out = new StringBuilder();
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        out.append("Car HUD Notification Analysis Export\n");
        out.append("Generated at: ").append(ts).append("\n\n");

        out.append("Summary\n");
        out.append("- Notification entries: ").append(entries.size()).append("\n");
        out.append("- TX payload entries: ").append(tx.size()).append("\n\n");

        out.append("Decision Counts\n");
        if (decisionCounts.isEmpty()) {
            out.append("- <none>\n");
        } else {
            for (Map.Entry<String, Integer> e : decisionCounts.entrySet()) {
                out.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        }
        out.append("\n");

        out.append("Package Counts\n");
        if (packageCounts.isEmpty()) {
            out.append("- <none>\n");
        } else {
            for (Map.Entry<String, Integer> e : packageCounts.entrySet()) {
                out.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        }
        out.append("\n");

        out.append("Recent TX JSON\n");
        if (tx.isEmpty()) {
            out.append("- <none>\n");
        } else {
            for (int i = 0; i < tx.size(); i++) {
                out.append(i + 1).append(". ").append(tx.get(i)).append("\n");
            }
        }
        out.append("\n");

        out.append("Raw Notification Entries\n");
        if (entries.isEmpty()) {
            out.append("- <none>\n");
        } else {
            for (String entry : entries) {
                out.append(entry).append("\n----------------------------------------\n");
            }
        }

        return out.toString();
    }

    @NonNull
    public static synchronized File exportAnalysisTxt(@NonNull Context context) throws IOException {
        File root = context.getExternalFilesDir("logs");
        if (root == null) {
            throw new IOException("External files dir unavailable");
        }
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Cannot create log folder");
        }

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File out = new File(root, "carhud_logs_" + stamp + ".txt");

        String report = buildAnalysisReport(context);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(report.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
        return out;
    }

    @Nullable
    private static String firstLine(@Nullable String text) {
        if (text == null) {
            return null;
        }
        int idx = text.indexOf('\n');
        return idx >= 0 ? text.substring(0, idx) : text;
    }

    @Nullable
    private static String extractPackage(@Nullable String firstLine) {
        if (firstLine == null) {
            return null;
        }
        int closeBracket = firstLine.indexOf(']');
        if (closeBracket < 0 || closeBracket + 1 >= firstLine.length()) {
            return null;
        }
        String rest = firstLine.substring(closeBracket + 1).trim();
        int nextSpace = rest.indexOf(' ');
        return nextSpace > 0 ? rest.substring(0, nextSpace).trim() : rest;
    }

    @Nullable
    private static String extractBetween(String text, String startToken, String endToken, int occurrence) {
        if (text == null || occurrence <= 0) {
            return null;
        }

        int from = 0;
        for (int i = 0; i < occurrence; i++) {
            int s = text.indexOf(startToken, from);
            if (s < 0) {
                return null;
            }
            int e = text.indexOf(endToken, s + startToken.length());
            if (e < 0) {
                return null;
            }
            if (i == occurrence - 1) {
                return text.substring(s + startToken.length(), e).trim();
            }
            from = e + endToken.length();
        }
        return null;
    }

    private static String safe(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return "<empty>";
        }
        return value.trim();
    }
}
