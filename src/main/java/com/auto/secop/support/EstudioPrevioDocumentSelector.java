package com.auto.secop.support;

import com.auto.secop.model.SecopArchivo;

import java.util.List;
import java.util.Locale;

/**
 * Picks Estudio Previo documents from a SECOP attachment listing.
 *
 * <p>Dedicated PDFs whose names contain {@code ESTUDIO PREVIO} / {@code Estudios Previos}
 * win over invitacion/document packages. ZIP packages are a fallback when no PDF matches.
 */
public final class EstudioPrevioDocumentSelector {

    private EstudioPrevioDocumentSelector() {
    }

    public static List<SecopArchivo> select(List<SecopArchivo> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<SecopArchivo> pdfs = files.stream()
                .filter(EstudioPrevioDocumentSelector::isEstudioPrevioPdf)
                .toList();
        if (!pdfs.isEmpty()) {
            return pdfs;
        }
        return files.stream()
                .filter(EstudioPrevioDocumentSelector::isInvitacionPackage)
                .toList();
    }

    public static boolean isEstudioPrevioPdf(SecopArchivo file) {
        if (file == null) {
            return false;
        }
        return isEstudioPrevioName(file.displayName())
                && SafePaths.looksLikePdf(file.displayName(), file.extension());
    }

    public static boolean isInvitacionPackage(SecopArchivo file) {
        if (file == null || !SafePaths.looksLikeZip(file.displayName(), file.extension())) {
            return false;
        }
        String squashed = TextNormalizer.squash(file.displayName());
        return squashed.contains("invitacion")
                || squashed.contains("documentos del proceso")
                || squashed.contains("anexos")
                || isEstudioPrevioName(file.displayName());
    }

    public static boolean isEstudioPrevioName(String fileName) {
        String squashed = TextNormalizer.squash(fileName);
        if (squashed.isBlank()) {
            return false;
        }
        return TextNormalizer.containsPhrase(squashed, "estudio previo")
                || TextNormalizer.containsPhrase(squashed, "estudios previos")
                || squashed.contains("estudio previo")
                || squashed.contains("estudios previos");
    }

    public static boolean isEstudioPrevioPdfName(String fileName) {
        return isEstudioPrevioName(fileName) && SafePaths.looksLikePdf(fileName, extensionOf(fileName));
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
