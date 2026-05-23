package com.hieptran.hubnotification;

import java.text.Normalizer;

public final class CarHudTextUtils {
    private CarHudTextUtils() {
    }

    public static String stripVietnameseDiacritics(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized
                .replaceAll("\\p{M}+", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .trim();
    }

    public static String limit(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
