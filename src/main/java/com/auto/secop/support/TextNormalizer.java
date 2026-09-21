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
        return stripAccents(raw).toLowerCase(Locale.ROOT).trim();
    }

    /** Removes combining marks but keeps letter case ({@code Caquetá} → {@code Caqueta}). */
    public static String stripAccents(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("");
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
        return containsWord(haystackNormalized, phraseNormalized)
                || containsInflectedWord(haystackNormalized, phraseNormalized);
    }

    /**
     * Matches Spanish gender/number inflections (logística ↔ logísticos) without
     * treating {@code cultura} as a hit inside {@code agricultura}.
     */
    public static boolean containsInflectedWord(String haystackNormalized, String wordNormalized) {
        String target = stem(wordNormalized);
        if (target.length() < 4) {
            return false;
        }
        for (String token : haystackNormalized.split(" ")) {
            if (token.isBlank()) {
                continue;
            }
            if (stem(token).equals(target)) {
                return true;
            }
        }
        return false;
    }

    static String stem(String token) {
        if (token == null || token.length() < 5) {
            return token == null ? "" : token;
        }
        if (token.endsWith("icos") || token.endsWith("icas")) {
            return token.substring(0, token.length() - 4);
        }
        if (token.endsWith("ico") || token.endsWith("ica")) {
            return token.substring(0, token.length() - 3);
        }
        if (token.endsWith("os") || token.endsWith("as") || token.endsWith("es")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("o") || token.endsWith("a") || token.endsWith("e")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
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
