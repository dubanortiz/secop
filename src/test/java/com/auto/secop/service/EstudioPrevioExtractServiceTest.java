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
        SecopProperties properties = new SecopProperties(
                "https://www.datos.gov.co/resource/p6dx-8zbt.json",
                "classpath:parametria/parametria-ofertas.json",
                80,
                200,
                20,
                List.of(),
                tempDir.toString(),
                "https://www.datos.gov.co/resource/dmgg-8hin.json"
        );
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
        given(ocrEngine.available()).willReturn(true);
        given(ocrEngine.recognize(anyList())).willAnswer(invocation -> {
            List<BufferedImage> pages = invocation.getArgument(0);
            assertThat(pages).isNotEmpty();
            return "Texto OCR del estudio previo de contratacion";
        });

        EstudioPrevioExtractResult result = service.extract(List.of(match())).getFirst();

        assertThat(result.status()).isEqualTo(EstudioPrevioExtractStatus.EXTRACTED);
        assertThat(result.files().getFirst().method()).isEqualTo(EstudioPrevioExtractMethod.OCR);
        Path textFile = tempDir.resolve(PROCESS_NUMBER).resolve("Estudio_Previo.txt");
        assertThat(Files.readString(textFile, StandardCharsets.UTF_8))
                .contains("Texto OCR del estudio previo de contratacion");
        assertThat(result.files().getFirst().chars()).isGreaterThan(0);
        verify(ocrEngine).recognize(anyList());
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
}
