package com.auto.secop.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds tiny PDFs for tests and committed fixtures.
 */
public final class EstudioPrevioPdfFixtures {

    public static final String NATIVE_TEXT = "Estudio Previo de contratacion para suministro de uniformes";

    private EstudioPrevioPdfFixtures() {
    }

    public static void writeTextPdf(Path dest) throws IOException {
        Files.createDirectories(dest.getParent());
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                stream.newLineAtOffset(72, 720);
                stream.showText(NATIVE_TEXT);
                stream.endText();
            }
            document.save(dest.toFile());
        }
    }

    public static void writeImageOnlyPdf(Path dest) throws IOException {
        Files.createDirectories(dest.getParent());
        BufferedImage image = new BufferedImage(640, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.PLAIN, 22));
        g.drawString("Estudio Previo OCR fixture", 20, 70);
        g.dispose();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(pdImage, 50, 650, 500, 90);
            }
            document.save(dest.toFile());
        }
    }
}
