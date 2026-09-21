package com.auto.secop.service;

import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.EstudioPrevioExtractMethod;
import com.auto.secop.model.EstudioPrevioExtractResult;
import com.auto.secop.model.EstudioPrevioExtractStatus;
import com.auto.secop.model.ProcessMatchResponse;
import com.auto.secop.ocr.EstudioPrevioPdfFixtures;
import com.auto.secop.ocr.PdfNativeTextExtractor;
import com.auto.secop.ocr.PdfOcrEngine;
import com.auto.secop.ocr.PdfPageRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EstudioPrevioExtractServiceTest {

    private static final String PROCESS_NUMBER = "MC-055-DISAN-EJC-2026";

    @TempDir
    Path tempDir;

    private PdfOcrEngine ocrEngine;
    private EstudioPrevioExtractService service;

    @BeforeEach
    void setUp() {
        ocrEngine = mock(PdfOcrEngine.class);
        SecopProperties properties = propertiesFor(tempDir);
        service = new EstudioPrevioExtractService(
                properties,
                new PdfNativeTextExtractor(),
                new PdfPageRenderer(),
                ocrEngine
        );
    }

    @Test
    void extractsEmbeddedPdfTextNextToThePdfWithoutOcr() throws Exception {
        Path pdf = tempDir.resolve(PROCESS_NUMBER).resolve("ESTUDIO PREVIO.pdf");
        EstudioPrevioPdfFixtures.writeTextPdf(pdf);

        EstudioPrevioExtractResult result = service.extract(List.of(match())).getFirst();

        assertThat(result.status()).isEqualTo(EstudioPrevioExtractStatus.EXTRACTED);
        assertThat(result.processNumber()).isEqualTo(PROCESS_NUMBER);
        assertThat(result.files()).hasSize(1);
        assertThat(result.files().getFirst().method()).isEqualTo(EstudioPrevioExtractMethod.TEXT);
        assertThat(result.files().getFirst().chars()).isGreaterThan(PdfNativeTextExtractor.MIN_NATIVE_ALNUM_CHARS);
        Path textFile = tempDir.resolve(PROCESS_NUMBER).resolve("ESTUDIO PREVIO.txt");
        assertThat(textFile).exists();
        assertThat(Files.readString(textFile, StandardCharsets.UTF_8)).contains(EstudioPrevioPdfFixtures.NATIVE_TEXT);
        assertThat(result.files().getFirst().pdf()).isEqualTo(pdf.toString());
        assertThat(result.files().getFirst().textFile()).isEqualTo(textFile.toString());
        verify(ocrEngine, never()).recognize(anyList());
        verify(ocrEngine, never()).available();
    }

    @Test
    void imageOnlyPdfTriggersOcrAndWritesSidecar() throws Exception {
        Path pdf = tempDir.resolve(PROCESS_NUMBER).resolve("Estudio_Previo.pdf");
        EstudioPrevioPdfFixtures.writeImageOnlyPdf(pdf);
        RecordingOcrEngine recording = new RecordingOcrEngine(
                "Texto OCR del estudio previo de contratacion para suministro de uniformes deportivos");
        service = new EstudioPrevioExtractService(
                propertiesFor(tempDir),
                new PdfNativeTextExtractor(),
                new PdfPageRenderer(),
                recording
        );

        EstudioPrevioExtractResult result = service.extract(List.of(match())).getFirst();

        assertThat(result.status()).as(result.error()).isEqualTo(EstudioPrevioExtractStatus.EXTRACTED);
        assertThat(result.files().getFirst().method()).isEqualTo(EstudioPrevioExtractMethod.OCR);
        Path textFile = tempDir.resolve(PROCESS_NUMBER).resolve("Estudio_Previo.txt");
        assertThat(Files.readString(textFile, StandardCharsets.UTF_8))
                .contains("Texto OCR del estudio previo de contratacion para suministro");
        assertThat(result.files().getFirst().chars()).isGreaterThan(0);
        assertThat(recording.pagesSeen).isNotEmpty();
    }

    @Test
    void missingPdfReturnsClearStatusWithoutInventingContent() {
        EstudioPrevioExtractResult result = service.extract(List.of(match())).getFirst();

        assertThat(result.status()).isEqualTo(EstudioPrevioExtractStatus.MISSING_PDF);
        assertThat(result.files()).isEmpty();
        assertThat(result.error()).containsIgnoringCase("run download first");
        assertThat(Files.exists(tempDir.resolve(PROCESS_NUMBER).resolve("ESTUDIO PREVIO.txt"))).isFalse();
    }

    @Test
    void emptyProcessFolderIsMissingPdf() throws Exception {
        Files.createDirectories(tempDir.resolve(PROCESS_NUMBER));

        EstudioPrevioExtractResult result = service.extract(List.of(match())).getFirst();

        assertThat(result.status()).isEqualTo(EstudioPrevioExtractStatus.MISSING_PDF);
        assertThat(result.files()).isEmpty();
    }

    @Test
    void scannedPdfWithoutOcrDoesNotWriteAFakeTxt() throws Exception {
        Path pdf = tempDir.resolve(PROCESS_NUMBER).resolve("ESTUDIO PREVIO.pdf");
        EstudioPrevioPdfFixtures.writeImageOnlyPdf(pdf);
        given(ocrEngine.available()).willReturn(false);

        EstudioPrevioExtractResult result = service.extract(List.of(match())).getFirst();

        assertThat(result.status()).isEqualTo(EstudioPrevioExtractStatus.ERROR);
        assertThat(result.files()).isEmpty();
        assertThat(result.error()).containsIgnoringCase("Tesseract");
        assertThat(Files.exists(tempDir.resolve(PROCESS_NUMBER).resolve("ESTUDIO PREVIO.txt"))).isFalse();
    }

    private static ProcessMatchResponse match() {
        return new ProcessMatchResponse(PROCESS_NUMBER, 90, "E", "O", "Mínima cuantía", 1L, "2026-09-22",
                "https://community.secop.gov.co/x");
    }

    private static SecopProperties propertiesFor(Path dir) {
        return new SecopProperties(
                "https://www.datos.gov.co/resource/p6dx-8zbt.json",
                "classpath:parametria/parametria-ofertas.json",
                80,
                200,
                20,
                List.of(),
                dir.toString(),
                "https://www.datos.gov.co/resource/dmgg-8hin.json"
        );
    }

    private static final class RecordingOcrEngine implements PdfOcrEngine {
        private final String text;
        private List<BufferedImage> pagesSeen = List.of();

        private RecordingOcrEngine(String text) {
            this.text = text;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String recognize(List<BufferedImage> pages) {
            this.pagesSeen = pages;
            return text;
        }
    }
}
