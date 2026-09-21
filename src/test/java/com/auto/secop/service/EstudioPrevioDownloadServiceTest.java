package com.auto.secop.service;

import com.auto.secop.client.DatosGovArchivosClient;
import com.auto.secop.client.DatosGovSecopClient;
import com.auto.secop.client.SecopDocumentClient;
import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.EstudioPrevioDownloadResult;
import com.auto.secop.model.EstudioPrevioDownloadStatus;
import com.auto.secop.model.ProcessMatchResponse;
import com.auto.secop.model.ProcessPortfolio;
import com.auto.secop.model.SecopArchivo;
import com.auto.secop.model.SecopProcess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EstudioPrevioDownloadServiceTest {

    private static final String PROCESS_NUMBER = "MC-055-DISAN-EJC-2026";
    private static final String PORTFOLIO = "CO1.BDOS.10836230";
    private static final String URL =
            "https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.10893271";
    private static final byte[] PDF = "%PDF-1.4 estudio previo fixture".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private DatosGovSecopClient processClient;
    private DatosGovArchivosClient archivosClient;
    private SecopDocumentClient documentClient;
    private EstudioPrevioDownloadService service;

    @BeforeEach
    void setUp() {
        processClient = mock(DatosGovSecopClient.class);
        archivosClient = mock(DatosGovArchivosClient.class);
        documentClient = mock(SecopDocumentClient.class);
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
        service = new EstudioPrevioDownloadService(processClient, archivosClient, documentClient, properties);
        given(processClient.findPortfolio(PROCESS_NUMBER, URL)).willReturn(Optional.of(portfolio()));
    }

    @Test
    void downloadsEstudioPrevioPdfIntoProcessFolder() throws Exception {
        SecopArchivo archivo = archivo("ESTUDIO PREVIO MOLECULARES 2026.pdf", "pdf", "https://community.secop.gov.co/file/1");
        given(archivosClient.findByPortfolio(PORTFOLIO)).willReturn(List.of(
                archivo,
                archivo("CDP.pdf", "pdf", "https://community.secop.gov.co/file/cdp")
        ));
        given(documentClient.download("https://community.secop.gov.co/file/1"))
                .willReturn(SecopDocumentClient.FetchedDocument.ok(PDF, "ESTUDIO PREVIO MOLECULARES 2026.pdf", "application/pdf"));

        List<EstudioPrevioDownloadResult> results = service.download(List.of(match()));

        assertThat(results).hasSize(1);
        EstudioPrevioDownloadResult result = results.getFirst();
        assertThat(result.status()).isEqualTo(EstudioPrevioDownloadStatus.DOWNLOADED);
        assertThat(result.processNumber()).isEqualTo(PROCESS_NUMBER);
        assertThat(result.files()).hasSize(1);
        assertThat(result.files().getFirst().fileName()).isEqualTo("ESTUDIO PREVIO MOLECULARES 2026.pdf");
        assertThat(result.files().getFirst().sizeBytes()).isEqualTo(PDF.length);
        Path saved = tempDir.resolve(PROCESS_NUMBER).resolve("ESTUDIO PREVIO MOLECULARES 2026.pdf");
        assertThat(saved).exists();
        assertThat(Files.readAllBytes(saved)).isEqualTo(PDF);
        verify(documentClient, never()).download("https://community.secop.gov.co/file/cdp");
    }

    @Test
    void skipsWhenFileAlreadyExists() throws Exception {
        Path dest = tempDir.resolve(PROCESS_NUMBER).resolve("ESTUDIO PREVIO.pdf");
        Files.createDirectories(dest.getParent());
        Files.write(dest, PDF);
        given(archivosClient.findByPortfolio(PORTFOLIO)).willReturn(List.of(
                archivo("ESTUDIO PREVIO.pdf", "pdf", "https://community.secop.gov.co/file/1")
        ));
        given(documentClient.download("https://community.secop.gov.co/file/1"))
                .willReturn(SecopDocumentClient.FetchedDocument.ok(PDF, "ESTUDIO PREVIO.pdf", "application/pdf"));

        EstudioPrevioDownloadResult result = service.download(List.of(match())).getFirst();

        assertThat(result.status()).isEqualTo(EstudioPrevioDownloadStatus.SKIPPED);
        assertThat(result.files().getFirst().path()).isEqualTo(dest.toString());
        assertThat(Files.readAllBytes(dest)).isEqualTo(PDF);
    }

    @Test
    void missingDocumentReturnsNotFound() {
        given(archivosClient.findByPortfolio(PORTFOLIO)).willReturn(List.of(
                archivo("CDP.pdf", "pdf", "https://community.secop.gov.co/file/cdp")
        ));
        given(documentClient.probeBlockedPage(URL)).willReturn(Optional.empty());

        EstudioPrevioDownloadResult result = service.download(List.of(match())).getFirst();

        assertThat(result.status()).isEqualTo(EstudioPrevioDownloadStatus.NOT_FOUND);
        assertThat(result.files()).isEmpty();
        assertThat(result.error()).contains("No Estudio Previo");
    }

    @Test
    void blockedDownloadReturnsBlockedWithoutWritingAFakePdf() {
        given(archivosClient.findByPortfolio(PORTFOLIO)).willReturn(List.of(
                archivo("ESTUDIO PREVIO.pdf", "pdf", "https://community.secop.gov.co/file/1")
        ));
        given(documentClient.download("https://community.secop.gov.co/file/1"))
                .willReturn(SecopDocumentClient.FetchedDocument.blocked(
                        "SECOP returned a reCAPTCHA/login page instead of the document; a human must complete captcha"));

        EstudioPrevioDownloadResult result = service.download(List.of(match())).getFirst();

        assertThat(result.status()).isEqualTo(EstudioPrevioDownloadStatus.BLOCKED);
        assertThat(result.files()).isEmpty();
        assertThat(result.error()).containsIgnoringCase("captcha");
        assertThat(Files.exists(tempDir.resolve(PROCESS_NUMBER).resolve("ESTUDIO PREVIO.pdf"))).isFalse();
    }

    private static ProcessMatchResponse match() {
        return new ProcessMatchResponse(PROCESS_NUMBER, 90, "DIRECCIÓN DE SANIDAD EJÉRCITO1",
                "SUMINISTRO", "Mínima cuantía", 52_000_010L, "2026-09-22", URL);
    }

    private static ProcessPortfolio portfolio() {
        return new ProcessPortfolio(PROCESS_NUMBER, PORTFOLIO, "CO1.REQ.11021745",
                new SecopProcess.UrlProceso(URL));
    }

    private static SecopArchivo archivo(String name, String extension, String url) {
        return new SecopArchivo("1", PORTFOLIO, name, String.valueOf(PDF.length), extension, name, new SecopArchivo.Url(url));
    }
}
