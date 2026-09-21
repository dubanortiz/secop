package com.auto.secop.ocr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PdfNativeTextExtractorTest {

    @TempDir
    Path tempDir;

    private final PdfNativeTextExtractor extractor = new PdfNativeTextExtractor();

    @Test
    void extractsEmbeddedTextFromShippedFixture() throws Exception {
        Path fixture = tempDir.resolve("estudio-previo-text.pdf");
        try (InputStream in = getClass().getResourceAsStream("/estudios-previos/estudio-previo-text.pdf")) {
            assertThat(in).as("shipped text PDF fixture").isNotNull();
            Files.copy(in, fixture);
        }
        PdfNativeTextExtractor.NativeText extracted = extractor.extract(fixture);

        assertThat(extracted.isSparse()).isFalse();
        assertThat(extracted.text()).contains(EstudioPrevioPdfFixtures.NATIVE_TEXT);
        assertThat(extracted.alphanumericCount()).isGreaterThanOrEqualTo(PdfNativeTextExtractor.MIN_NATIVE_ALNUM_CHARS);
    }

    @Test
    void imageOnlyPdfIsSparse() throws Exception {
        Path pdf = tempDir.resolve("scanned.pdf");
        EstudioPrevioPdfFixtures.writeImageOnlyPdf(pdf);

        PdfNativeTextExtractor.NativeText extracted = extractor.extract(pdf);

        assertThat(extracted.isSparse()).isTrue();
        assertThat(extracted.alphanumericCount()).isLessThan(PdfNativeTextExtractor.MIN_NATIVE_ALNUM_CHARS);
    }
}
