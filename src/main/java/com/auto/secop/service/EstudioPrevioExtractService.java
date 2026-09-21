package com.auto.secop.service;

import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.EstudioPrevioExtractMethod;
import com.auto.secop.model.EstudioPrevioExtractResult;
import com.auto.secop.model.EstudioPrevioExtractResult.ExtractedFile;
import com.auto.secop.model.EstudioPrevioExtractStatus;
import com.auto.secop.model.ProcessMatchResponse;
import com.auto.secop.ocr.PdfNativeTextExtractor;
import com.auto.secop.ocr.PdfNativeTextExtractor.NativeText;
import com.auto.secop.ocr.PdfOcrEngine;
import com.auto.secop.ocr.PdfPageRenderer;
import com.auto.secop.support.SafePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Extracts plain text from Estudio Previo PDFs already on disk (paso 2 download layout).
 * Native PDFBox text first; OCR (Tesseract) only when the PDF looks scanned.
 */
@Service
public class EstudioPrevioExtractService {

    private static final Logger log = LoggerFactory.getLogger(EstudioPrevioExtractService.class);
    static final String OCR_UNAVAILABLE =
            "PDF appears scanned (little or no extractable text) and Tesseract OCR is not available. "
                    + "Install tesseract with spa traineddata (and optionally eng). Native text extraction "
                    + "does not require Tesseract.";

    private final SecopProperties properties;
    private final PdfNativeTextExtractor nativeTextExtractor;
    private final PdfPageRenderer pageRenderer;
    private final PdfOcrEngine ocrEngine;

    public EstudioPrevioExtractService(
            SecopProperties properties,
            PdfNativeTextExtractor nativeTextExtractor,
            PdfPageRenderer pageRenderer,
            PdfOcrEngine ocrEngine
    ) {
        this.properties = properties;
        this.nativeTextExtractor = nativeTextExtractor;
        this.pageRenderer = pageRenderer;
        this.ocrEngine = ocrEngine;
    }

    public List<EstudioPrevioExtractResult> extract(List<ProcessMatchResponse> processes) {
        if (processes == null || processes.isEmpty()) {
            return List.of();
        }
        Path root = Path.of(properties.safeEstudiosPreviosDir()).toAbsolutePath().normalize();
        List<EstudioPrevioExtractResult> results = new ArrayList<>();
        for (ProcessMatchResponse process : processes) {
            results.add(extractOne(process, root));
        }
        return List.copyOf(results);
    }

    private EstudioPrevioExtractResult extractOne(ProcessMatchResponse process, Path root) {
        if (process == null) {
            return EstudioPrevioExtractResult.of(null, EstudioPrevioExtractStatus.ERROR, List.of(),
                    "Process entry is required");
        }
        String processNumber = process.processNumber();
        Path processDir = root.resolve(SafePaths.processFolder(processNumber)).normalize();
        if (!processDir.startsWith(root) || !Files.isDirectory(processDir)) {
            return EstudioPrevioExtractResult.of(
                    processNumber,
                    EstudioPrevioExtractStatus.MISSING_PDF,
                    List.of(),
                    "No Estudio Previo PDF on disk under " + processDir + " — run download first"
            );
        }
        List<Path> pdfs;
        try {
            pdfs = listPdfs(processDir);
        } catch (IOException ex) {
            return EstudioPrevioExtractResult.of(
                    processNumber,
                    EstudioPrevioExtractStatus.ERROR,
                    List.of(),
                    "Unable to list PDFs: " + ex.getMessage()
            );
        }
        if (pdfs.isEmpty()) {
            return EstudioPrevioExtractResult.of(
                    processNumber,
                    EstudioPrevioExtractStatus.MISSING_PDF,
                    List.of(),
                    "No Estudio Previo PDF on disk under " + processDir + " — run download first"
            );
        }
        List<ExtractedFile> files = new ArrayList<>();
        String lastError = null;
        for (Path pdf : pdfs) {
            try {
                files.add(extractPdf(pdf));
            } catch (IOException | RuntimeException ex) {
                lastError = ex.getMessage();
                log.warn("Failed to extract text from {}: {}", pdf, ex.getMessage());
            }
        }
        if (!files.isEmpty()) {
            return EstudioPrevioExtractResult.of(processNumber, EstudioPrevioExtractStatus.EXTRACTED, files, lastError);
        }
        return EstudioPrevioExtractResult.of(
                processNumber,
                EstudioPrevioExtractStatus.ERROR,
                List.of(),
                lastError == null ? "Unable to extract text" : lastError
        );
    }

    private ExtractedFile extractPdf(Path pdf) throws IOException {
        NativeText nativeText = nativeTextExtractor.extract(pdf);
        String text;
        EstudioPrevioExtractMethod method;
        if (!nativeText.isSparse()) {
            text = nativeText.text();
            method = EstudioPrevioExtractMethod.TEXT;
        } else if (!ocrEngine.available()) {
            throw new IOException(OCR_UNAVAILABLE);
        } else {
            List<BufferedImage> pages = pageRenderer.render(pdf);
            text = ocrEngine.recognize(pages);
            if (text == null || text.codePoints().filter(Character::isLetterOrDigit).count()
                    < PdfNativeTextExtractor.MIN_NATIVE_ALNUM_CHARS) {
                throw new IOException("OCR produced little or no text for " + pdf.getFileName());
            }
            method = EstudioPrevioExtractMethod.OCR;
        }
        Path sidecar = SafePaths.textSidecar(pdf);
        Files.writeString(sidecar, normalize(text), StandardCharsets.UTF_8);
        String written = Files.readString(sidecar, StandardCharsets.UTF_8);
        log.info("Wrote Estudio Previo text {} ({} chars, {})", sidecar, written.length(), method.json());
        return new ExtractedFile(pdf.toString(), sidecar.toString(), method, written.length());
    }

    private static List<Path> listPdfs(Path processDir) throws IOException {
        try (Stream<Path> stream = Files.list(processDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(SafePaths::looksLikePdf)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').strip() + "\n";
    }
}
