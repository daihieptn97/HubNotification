package com.hieptran.hubnotification;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoogleMapsNavParser {
    public static final class NavInfo {
        public final String arrow;
        public final int distance;
        public final String unit;
        public final String street;

        public NavInfo(String arrow, int distance, String unit, String street) {
            this.arrow = arrow;
            this.distance = distance;
            this.unit = unit;
            this.street = street;
        }
    }

    private static final Pattern DISTANCE_PATTERN =
            Pattern.compile("In\\s+(\\d+(?:\\.\\d+)?)\\s*(km|m|mi|ft)", Pattern.CASE_INSENSITIVE);

    private static final LinkedHashMap<Pattern, String> ACTION_PATTERNS = new LinkedHashMap<>();

    static {
        ACTION_PATTERNS.put(Pattern.compile("turn right", Pattern.CASE_INSENSITIVE), "right");
        ACTION_PATTERNS.put(Pattern.compile("turn left", Pattern.CASE_INSENSITIVE), "left");
        ACTION_PATTERNS.put(Pattern.compile("slight right", Pattern.CASE_INSENSITIVE), "slight-right");
        ACTION_PATTERNS.put(Pattern.compile("slight left", Pattern.CASE_INSENSITIVE), "slight-left");
        ACTION_PATTERNS.put(Pattern.compile("sharp right", Pattern.CASE_INSENSITIVE), "sharp-right");
        ACTION_PATTERNS.put(Pattern.compile("sharp left", Pattern.CASE_INSENSITIVE), "sharp-left");
        ACTION_PATTERNS.put(Pattern.compile("u-?turn", Pattern.CASE_INSENSITIVE), "uturn");
        ACTION_PATTERNS.put(Pattern.compile("continue|straight|head", Pattern.CASE_INSENSITIVE), "straight");
    }

    private GoogleMapsNavParser() {
    }

    @Nullable
    public static NavInfo parse(@Nullable String title, @Nullable String text, @Nullable String subText) {
        if (isBlank(text)) {
            return null;
        }

        String titleValue = title == null ? "" : title;
        String textValue = text.trim();
        String street = extractStreet(textValue, subText);

        String arrow = "straight";
        for (Map.Entry<Pattern, String> entry : ACTION_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(textValue).find()) {
                arrow = entry.getValue();
                break;
            }
        }

        Matcher matcher = DISTANCE_PATTERN.matcher(titleValue);
        if (matcher.find()) {
            float value = parseFloatSafely(matcher.group(1));
            String unitRaw = matcher.group(2).toLowerCase(Locale.US);
            DistResult distResult = normalizeDistance(value, unitRaw);
            return new NavInfo(arrow, distResult.distance, distResult.unit, street);
        }

        if (titleValue.equalsIgnoreCase("Now") || titleValue.toLowerCase(Locale.US).contains("now")) {
            return new NavInfo(arrow, 0, "m", street);
        }

        return null;
    }

    private static String extractStreet(String text, @Nullable String subText) {
        int idx = indexOfAnyIgnoreCase(text, " onto ", " toward ", " on ");
        if (idx >= 0) {
            String marker = markerAt(text, idx);
            return text.substring(idx + marker.length()).trim();
        }
        if (!isBlank(subText)) {
            return subText.trim();
        }
        return text.trim();
    }

    private static int indexOfAnyIgnoreCase(String text, String... needles) {
        String lower = text.toLowerCase(Locale.US);
        int best = -1;
        for (String needle : needles) {
            int found = lower.indexOf(needle.trim().toLowerCase(Locale.US));
            if (found >= 0 && (best == -1 || found < best)) {
                best = found;
            }
        }
        return best;
    }

    private static String markerAt(String text, int idx) {
        String lower = text.toLowerCase(Locale.US);
        if (lower.startsWith(" onto ", idx)) {
            return " onto ";
        }
        if (lower.startsWith(" toward ", idx)) {
            return " toward ";
        }
        return " on ";
    }

    private static DistResult normalizeDistance(float value, String unit) {
        if ("km".equals(unit)) {
            return new DistResult(Math.max(0, Math.round(value)), "km");
        }
        if ("m".equals(unit)) {
            return new DistResult(Math.max(0, Math.round(value)), "m");
        }
        if ("mi".equals(unit)) {
            return new DistResult(Math.max(0, Math.round(value * 1.609f)), "km");
        }
        if ("ft".equals(unit)) {
            return new DistResult(Math.max(0, Math.round(value * 0.305f)), "m");
        }
        return new DistResult(Math.max(0, Math.round(value)), "m");
    }

    private static float parseFloatSafely(@Nullable String raw) {
        if (raw == null) {
            return 0f;
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
            return 0f;
        }
    }

    private static boolean isBlank(@Nullable String text) {
        return text == null || text.trim().isEmpty();
    }

    private static final class DistResult {
        final int distance;
        final String unit;

        DistResult(int distance, String unit) {
            this.distance = distance;
            this.unit = unit;
        }
    }
}
