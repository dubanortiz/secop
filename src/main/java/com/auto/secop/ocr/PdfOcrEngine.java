package com.auto.secop.ocr;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * Optional OCR for scanned / image-only PDFs. Native PDF text extraction does not need this.
 */
public interface PdfOcrEngine {

    boolean available();

    /**
     * OCR already-rasterized PDF pages (PDFBox). Concatenates page text.
     */
    String recognize(List<BufferedImage> pages) throws IOException;
}
