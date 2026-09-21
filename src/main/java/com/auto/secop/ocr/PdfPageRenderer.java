package com.auto.secop.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Rasterizes PDF pages for Tesseract when native text is missing.
 */
@Component
public class PdfPageRenderer {

    public static final float OCR_DPI = 200f;

    public List<BufferedImage> render(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = document.getNumberOfPages();
            List<BufferedImage> images = new ArrayList<>(pages);
            for (int i = 0; i < pages; i++) {
                images.add(renderer.renderImageWithDPI(i, OCR_DPI, ImageType.RGB));
            }
            return List.copyOf(images);
        }
    }
}
