package com.auto.secop.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Native (embedded) PDF text via Apache PDFBox. Does not require Tesseract.
 */
@Component
public class PdfNativeTextExtractor {

    /**
     * Below this many letters/digits, the PDF is treated as scanned and OCR is attempted.
     */
    public static final int MIN_NATIVE_ALNUM_CHARS = 50;

    public NativeText extract(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return new NativeText(text == null ? "" : text, document.getNumberOfPages());
        }
    }

    public record NativeText(String text, int pageCount) {

        public long alphanumericCount() {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            return text.codePoints().filter(Character::isLetterOrDigit).count();
        }

        public boolean isSparse() {
            return alphanumericCount() < MIN_NATIVE_ALNUM_CHARS;
        }
    }
}
