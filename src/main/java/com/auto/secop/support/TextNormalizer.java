package com.auto.secop.support;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Accent-insensitive, lowercase comparison helpers for SECOP text and parametría keywords.
 */
public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private TextNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return withoutMarks.toLowerCase(Locale.ROOT).trim();
    }

    public static String squash(String raw) {
        String normalized = normalize(raw);
        return NON_ALNUM.matcher(normalized).replaceAll(" ").trim();
    }

    public static boolean containsPhrase(String haystackNormalized, String phraseNormalized) {
        if (haystackNormalized.isEmpty() || phraseNormalized.isEmpty()) {
            return false;
        }
        if (phraseNormalized.contains(" ")) {
            return haystackNormalized.contains(phraseNormalized);
        }
        return containsWord(haystackNormalized, phraseNormalized);
    }

    public static boolean containsWord(String haystackNormalized, String wordNormalized) {
        if (haystackNormalized.isEmpty() || wordNormalized.isEmpty()) {
            return false;
        }
        int from = 0;
        while (from <= haystackNormalized.length() - wordNormalized.length()) {
            int idx = haystackNormalized.indexOf(wordNormalized, from);
            if (idx < 0) {
                return false;
            }
            boolean startOk = idx == 0 || !isTokenChar(haystackNormalized.charAt(idx - 1));
            int end = idx + wordNormalized.length();
            boolean endOk = end == haystackNormalized.length() || !isTokenChar(haystackNormalized.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            from = idx + 1;
        }
        return false;
    }

    private static boolean isTokenChar(char c) {
        return Character.isLetterOrDigit(c);
    }
}
