package com.auto.secop.support;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safe folder/file names for downloaded Estudio Previo documents.
 */
public final class SafePaths {

    private static final Pattern NOTICE_UID = Pattern.compile("noticeUID=([^&]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSAFE_FOLDER = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final Pattern MULTI_DASH = Pattern.compile("-{2,}");
    private static final Pattern EDGE_DOTS = Pattern.compile("^[.-]+|[.-]+$");
    private static final Pattern UNSAFE_FILE = Pattern.compile("[\\x00-\\x1F<>:\"|?*\\\\/]");

    private SafePaths() {
    }

    public static String processFolder(String processNumber) {
        String trimmed = processNumber == null ? "" : processNumber.trim();
        String cleaned = UNSAFE_FOLDER.matcher(trimmed).replaceAll("-");
        cleaned = MULTI_DASH.matcher(cleaned).replaceAll("-");
        cleaned = EDGE_DOTS.matcher(cleaned).replaceAll("");
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            return "sin-numero";
        }
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    public static String fileName(String raw) {
        String name = raw == null ? "" : Path.of(raw.replace('\\', '/')).getFileName().toString();
        name = UNSAFE_FILE.matcher(name).replaceAll("_").trim();
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            return "estudio-previo.pdf";
        }
        return name.length() > 180 ? name.substring(0, 180) : name;
    }

    public static Optional<String> noticeUid(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = NOTICE_UID.matcher(url);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).trim());
    }

    public static String canonicalOpportunityUrl(String url) {
        return noticeUid(url)
                .map(uid -> "https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=" + uid)
                .orElse(url == null ? "" : url.trim());
    }

    public static boolean looksLikePdf(String fileName, String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT).trim();
        if (ext.equals("pdf")) {
            return true;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf");
    }

    public static boolean looksLikePdf(Path file) {
        return file != null && looksLikePdf(file.getFileName().toString(), null);
    }

    /**
     * {@code ESTUDIO PREVIO.pdf} → {@code ESTUDIO PREVIO.txt} in the same folder.
     */
    public static Path textSidecar(Path pdf) {
        String name = pdf.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return pdf.resolveSibling(base + ".txt");
    }

    public static boolean looksLikeZip(String fileName, String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT).trim();
        if (ext.equals("zip")) {
            return true;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".zip");
    }
}
