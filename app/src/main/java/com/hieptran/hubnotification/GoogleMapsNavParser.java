package com.hieptran.hubnotification;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
            Pattern.compile("(?:in|sau|after)?\\s*(\\d+(?:[.,]\\d+)?)\\s*(km|m|mi|ft|met|meter|meters)", Pattern.CASE_INSENSITIVE);

    private static final LinkedHashMap<Pattern, String> ACTION_PATTERNS = new LinkedHashMap<>();
    private static final Pattern NOW_PATTERN =
            Pattern.compile("\\b(now|bay gio|ngay bay gio)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STRIP_PUNCTUATION = Pattern.compile("[.:,;!?]$");

    static {
        ACTION_PATTERNS.put(Pattern.compile("turn right", Pattern.CASE_INSENSITIVE), "right");
        ACTION_PATTERNS.put(Pattern.compile("turn left", Pattern.CASE_INSENSITIVE), "left");
        ACTION_PATTERNS.put(Pattern.compile("slight right", Pattern.CASE_INSENSITIVE), "slight-right");
        ACTION_PATTERNS.put(Pattern.compile("slight left", Pattern.CASE_INSENSITIVE), "slight-left");
        ACTION_PATTERNS.put(Pattern.compile("sharp right", Pattern.CASE_INSENSITIVE), "sharp-right");
        ACTION_PATTERNS.put(Pattern.compile("sharp left", Pattern.CASE_INSENSITIVE), "sharp-left");
        ACTION_PATTERNS.put(Pattern.compile("u-?turn", Pattern.CASE_INSENSITIVE), "uturn");
        ACTION_PATTERNS.put(Pattern.compile("continue|straight|head", Pattern.CASE_INSENSITIVE), "straight");
        ACTION_PATTERNS.put(Pattern.compile("re phai|r\\u1ebd ph\\u1ea3i", Pattern.CASE_INSENSITIVE), "right");
        ACTION_PATTERNS.put(Pattern.compile("re trai|r\\u1ebd tr\\u00e1i", Pattern.CASE_INSENSITIVE), "left");
        ACTION_PATTERNS.put(Pattern.compile("chech phai", Pattern.CASE_INSENSITIVE), "slight-right");
        ACTION_PATTERNS.put(Pattern.compile("chech trai", Pattern.CASE_INSENSITIVE), "slight-left");
        ACTION_PATTERNS.put(Pattern.compile("g\\u1ea5p ph\\u1ea3i|gap phai", Pattern.CASE_INSENSITIVE), "sharp-right");
        ACTION_PATTERNS.put(Pattern.compile("g\\u1ea5p tr\\u00e1i|gap trai", Pattern.CASE_INSENSITIVE), "sharp-left");
        ACTION_PATTERNS.put(Pattern.compile("quay d\\u1ea7u|quay dau", Pattern.CASE_INSENSITIVE), "uturn");
        ACTION_PATTERNS.put(Pattern.compile("di thang|\\u0111i th\\u1eb3ng|ti\\u1ebfp t\\u1ee5c|tiep tuc", Pattern.CASE_INSENSITIVE), "straight");
    }

    private GoogleMapsNavParser() {
    }

    @Nullable
    public static NavInfo parse(@Nullable String title, @Nullable String text, @Nullable String subText) {
        if (isBlank(title) && isBlank(text) && isBlank(subText)) {
            return null;
        }

        String titleValue = valueOrEmpty(title);
        String textValue = valueOrEmpty(text);
        String subTextValue = valueOrEmpty(subText);
        String combined = (titleValue + " " + textValue + " " + subTextValue).trim();

        String street = extractStreet(textValue, subTextValue);
        if (street.isEmpty()) {
            street = extractStreet(combined, subTextValue);
        }

        String arrow = "straight";
        String resolvedArrow = resolveArrow(textValue, titleValue, subTextValue);
        if (!isBlank(resolvedArrow)) {
            arrow = resolvedArrow;
        }

        Matcher matcher = DISTANCE_PATTERN.matcher(combined);
        if (matcher.find()) {
            float value = parseFloatSafely(matcher.group(1).replace(',', '.'));
            String unitRaw = matcher.group(2).toLowerCase(Locale.US);
            DistResult distResult = normalizeDistance(value, unitRaw);
            return new NavInfo(arrow, distResult.distance, distResult.unit, cleanStreet(street));
        }

        if (NOW_PATTERN.matcher(combined).find()) {
            return new NavInfo(arrow, 0, "m", cleanStreet(street));
        }

        if (!isBlank(arrow) && !isBlank(street)) {
            return new NavInfo(arrow, 0, "m", cleanStreet(street));
        }
        return null;
    }

    private static String resolveArrow(String... candidates) {
        for (String candidate : candidates) {
            if (isBlank(candidate)) {
                continue;
            }
            for (Map.Entry<Pattern, String> entry : ACTION_PATTERNS.entrySet()) {
                if (entry.getKey().matcher(candidate).find()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static String extractStreet(String text, @Nullable String subText) {
        if (isBlank(text)) {
            return isBlank(subText) ? "" : subText.trim();
        }

        int idx = indexOfAnyIgnoreCase(
                text,
                " onto ", " toward ", " on ",
                " vào ", " vao ", " tới ", " toi ", " đến ", " den ", " theo "
        );
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
            int found = lower.indexOf(needle.toLowerCase(Locale.US));
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
        if (lower.startsWith(" vào ", idx)) {
            return " vào ";
        }
        if (lower.startsWith(" vao ", idx)) {
            return " vao ";
        }
        if (lower.startsWith(" tới ", idx)) {
            return " tới ";
        }
        if (lower.startsWith(" toi ", idx)) {
            return " toi ";
        }
        if (lower.startsWith(" đến ", idx)) {
            return " đến ";
        }
        if (lower.startsWith(" den ", idx)) {
            return " den ";
        }
        if (lower.startsWith(" theo ", idx)) {
            return " theo ";
        }
        return " on ";
    }

    private static DistResult normalizeDistance(float value, String unit) {
        if ("km".equals(unit)) {
            return new DistResult(Math.max(0, Math.round(value * 1000f)), "m");
        }
        if ("m".equals(unit) || "met".equals(unit) || "meter".equals(unit) || "meters".equals(unit)) {
            return new DistResult(Math.max(0, Math.round(value)), "m");
        }
        if ("mi".equals(unit)) {
            return new DistResult(Math.max(0, Math.round(value * 1609.344f)), "m");
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

    private static String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static String cleanStreet(String street) {
        if (isBlank(street)) {
            return "";
        }
        String cleaned = street.trim();
        cleaned = STRIP_PUNCTUATION.matcher(cleaned).replaceAll("").trim();

        List<String> noiseTokens = new ArrayList<>();
        noiseTokens.add("tap de xem");
        noiseTokens.add("tap to view");
        noiseTokens.add("notification");

        String lower = cleaned.toLowerCase(Locale.US);
        for (String token : noiseTokens) {
            int idx = lower.indexOf(token);
            if (idx > 0) {
                cleaned = cleaned.substring(0, idx).trim();
                break;
            }
        }
        return cleaned;
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
