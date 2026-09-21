package com.auto.secop.ocr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional: runs only when Tesseract + spa traineddata are installed.
 * Default {@code ./mvnw test} skips this when Tesseract is absent.
 */
class TesseractPdfOcrEngineIntegrationTest {

    @TempDir
    Path tempDir;

    static boolean tesseractWithSpanishData() {
        return TesseractPdfOcrEngine.tesseractWithSpanishData();
    }

    @EnabledIf("tesseractWithSpanishData")
    @Test
    void ocrReadsImageOnlyPdfWhenTesseractIsInstalled() throws Exception {
        Path pdf = tempDir.resolve("scanned.pdf");
        EstudioPrevioPdfFixtures.writeImageOnlyPdf(pdf);
        PdfPageRenderer renderer = new PdfPageRenderer();
        TesseractPdfOcrEngine engine = new TesseractPdfOcrEngine();

        assertThat(engine.available()).isTrue();
        String text = engine.recognize(renderer.render(pdf));

        assertThat(text.toLowerCase(Locale.ROOT)).contains("estudio");
    }
}
