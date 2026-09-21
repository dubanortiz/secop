package com.auto.secop.service;

import com.auto.secop.client.DatosGovArchivosClient;
import com.auto.secop.client.DatosGovSecopClient;
import com.auto.secop.client.DatosGovUnavailableException;
import com.auto.secop.client.SecopDocumentClient;
import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.EstudioPrevioDownloadResult;
import com.auto.secop.model.EstudioPrevioDownloadResult.SavedFile;
import com.auto.secop.model.EstudioPrevioDownloadStatus;
import com.auto.secop.model.ProcessMatchResponse;
import com.auto.secop.model.ProcessPortfolio;
import com.auto.secop.model.SecopArchivo;
import com.auto.secop.support.EstudioPrevioDocumentSelector;
import com.auto.secop.support.SafePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads Estudio Previo attachments listed on the public datos.gov.co archivos dataset.
 * Does not apply to tenders, submit offers, or mutate SECOP.
 *
 * <p>Idempotency: if a file with the same name already exists in the process folder and is
 * non-empty, it is skipped (not overwritten).
 */
@Service
public class EstudioPrevioDownloadService {

    private static final Logger log = LoggerFactory.getLogger(EstudioPrevioDownloadService.class);

    private final DatosGovSecopClient processClient;
    private final DatosGovArchivosClient archivosClient;
    private final SecopDocumentClient documentClient;
    private final SecopProperties properties;

    public EstudioPrevioDownloadService(
            DatosGovSecopClient processClient,
            DatosGovArchivosClient archivosClient,
            SecopDocumentClient documentClient,
            SecopProperties properties
    ) {
        this.processClient = processClient;
        this.archivosClient = archivosClient;
        this.documentClient = documentClient;
        this.properties = properties;
    }

    public List<EstudioPrevioDownloadResult> download(List<ProcessMatchResponse> processes) {
        if (processes == null || processes.isEmpty()) {
            return List.of();
        }
        Path root = Path.of(properties.safeEstudiosPreviosDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create estudios-previos directory: " + root, ex);
        }
        List<EstudioPrevioDownloadResult> results = new ArrayList<>();
        for (ProcessMatchResponse process : processes) {
            results.add(downloadOne(process, root));
        }
        return List.copyOf(results);
    }

    private EstudioPrevioDownloadResult downloadOne(ProcessMatchResponse process, Path root) {
        if (process == null) {
            return EstudioPrevioDownloadResult.of(null, EstudioPrevioDownloadStatus.ERROR, List.of(),
                    "Process entry is required");
        }
        String processNumber = process.processNumber();
        try {
            Optional<ProcessPortfolio> portfolio = processClient.findPortfolio(processNumber, process.url());
            if (portfolio.isEmpty() || !portfolio.get().hasPortfolioId()) {
                return blockedOrMissing(processNumber, process.url(),
                        "Process not found on datos.gov.co (need processNumber or OpportunityDetail url)");
            }
            List<SecopArchivo> listed = archivosClient.findByPortfolio(portfolio.get().portfolioId());
            List<SecopArchivo> selected = EstudioPrevioDocumentSelector.select(listed);
            if (selected.isEmpty()) {
                return blockedOrMissing(processNumber, process.url(),
                        "No Estudio Previo PDF (or invitacion package containing one) listed on datos.gov.co archivos");
            }
            Path processDir = root.resolve(SafePaths.processFolder(processNumber));
            Files.createDirectories(processDir);
            List<SavedFile> saved = new ArrayList<>();
            boolean downloadedNew = false;
            String blocked = null;
            String error = null;
            for (SecopArchivo archivo : selected) {
                FileOutcome outcome = saveArchivo(archivo, processDir);
                saved.addAll(outcome.files());
                downloadedNew = downloadedNew || outcome.downloadedNew();
                if (outcome.blocked() != null) {
                    blocked = outcome.blocked();
                } else if (outcome.error() != null) {
                    error = outcome.error();
                }
            }
            if (!saved.isEmpty()) {
                EstudioPrevioDownloadStatus status = downloadedNew
                        ? EstudioPrevioDownloadStatus.DOWNLOADED
                        : EstudioPrevioDownloadStatus.SKIPPED;
                return EstudioPrevioDownloadResult.of(processNumber, status, saved, null);
            }
            if (blocked != null) {
                return EstudioPrevioDownloadResult.of(processNumber, EstudioPrevioDownloadStatus.BLOCKED, List.of(), blocked);
            }
            return EstudioPrevioDownloadResult.of(
                    processNumber,
                    EstudioPrevioDownloadStatus.ERROR,
                    List.of(),
                    error == null ? "Unable to save Estudio Previo" : error
            );
        } catch (DatosGovUnavailableException ex) {
            log.warn("datos.gov.co unavailable while downloading estudios previos for {}: {}", processNumber, ex.getMessage());
            return EstudioPrevioDownloadResult.of(processNumber, EstudioPrevioDownloadStatus.ERROR, List.of(), ex.getMessage());
        } catch (RuntimeException | IOException ex) {
            log.warn("Failed to download estudios previos for {}: {}", processNumber, ex.getMessage());
            return EstudioPrevioDownloadResult.of(processNumber, EstudioPrevioDownloadStatus.ERROR, List.of(), ex.getMessage());
        }
    }

    private EstudioPrevioDownloadResult blockedOrMissing(String processNumber, String url, String missingMessage) {
        Optional<String> blocked = documentClient.probeBlockedPage(url);
        if (blocked.isPresent()) {
            return EstudioPrevioDownloadResult.of(
                    processNumber,
                    EstudioPrevioDownloadStatus.BLOCKED,
                    List.of(),
                    blocked.get() + ". " + missingMessage
            );
        }
        return EstudioPrevioDownloadResult.of(processNumber, EstudioPrevioDownloadStatus.NOT_FOUND, List.of(), missingMessage);
    }

    private FileOutcome saveArchivo(SecopArchivo archivo, Path processDir) throws IOException {
        String url = archivo.downloadUrl();
        SecopDocumentClient.FetchedDocument fetched = documentClient.download(url);
        if (fetched.blocked()) {
            return FileOutcome.blocked(fetched.detail());
        }
        if (fetched.error() || fetched.content() == null) {
            return FileOutcome.error(fetched.detail() == null ? "Download failed" : fetched.detail());
        }
        String suggested = fetched.fileName() != null ? fetched.fileName() : archivo.displayName();
        if (EstudioPrevioDocumentSelector.isEstudioPrevioPdf(archivo)
                || EstudioPrevioDocumentSelector.isEstudioPrevioPdfName(suggested)) {
            return saveBytes(processDir, suggested, fetched.content());
        }
        if (SafePaths.looksLikeZip(suggested, archivo.extension())
                || SafePaths.looksLikeZip(archivo.displayName(), archivo.extension())) {
            return extractEstudioPrevioFromZip(processDir, fetched.content());
        }
        if (EstudioPrevioDocumentSelector.isEstudioPrevioName(suggested)
                || EstudioPrevioDocumentSelector.isEstudioPrevioName(archivo.displayName())) {
            return saveBytes(processDir, suggested, fetched.content());
        }
        return FileOutcome.error("Downloaded attachment was not an Estudio Previo PDF");
    }

    private FileOutcome extractEstudioPrevioFromZip(Path processDir, byte[] zipBytes) throws IOException {
        List<SavedFile> saved = new ArrayList<>();
        boolean downloadedNew = false;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = SafePaths.fileName(entry.getName());
                if (!EstudioPrevioDocumentSelector.isEstudioPrevioPdfName(name)) {
                    continue;
                }
                byte[] content = zip.readAllBytes();
                if (content.length > SecopDocumentClient.MAX_BYTES) {
                    continue;
                }
                FileOutcome one = saveBytes(processDir, name, content);
                saved.addAll(one.files());
                downloadedNew = downloadedNew || one.downloadedNew();
            }
        }
        if (saved.isEmpty()) {
            return FileOutcome.error("Invitacion package did not contain an Estudio Previo PDF");
        }
        return new FileOutcome(saved, downloadedNew, null, null);
    }

    private FileOutcome saveBytes(Path processDir, String rawName, byte[] content) throws IOException {
        String fileName = SafePaths.fileName(rawName);
        Path dest = processDir.resolve(fileName).normalize();
        if (!dest.startsWith(processDir)) {
            return FileOutcome.error("Rejected path-traversal filename: " + rawName);
        }
        if (Files.exists(dest) && Files.size(dest) > 0) {
            return new FileOutcome(
                    List.of(new SavedFile(fileName, dest.toString(), Files.size(dest))),
                    false,
                    null,
                    null
            );
        }
        Files.write(dest, content);
        log.info("Saved Estudio Previo {} ({} bytes)", dest, content.length);
        return new FileOutcome(
                List.of(new SavedFile(fileName, dest.toString(), content.length)),
                true,
                null,
                null
        );
    }

    private record FileOutcome(
            List<SavedFile> files,
            boolean downloadedNew,
            String blocked,
            String error
    ) {
        static FileOutcome blocked(String detail) {
            return new FileOutcome(List.of(), false, detail, null);
        }

        static FileOutcome error(String detail) {
            return new FileOutcome(List.of(), false, null, detail);
        }
    }
}
