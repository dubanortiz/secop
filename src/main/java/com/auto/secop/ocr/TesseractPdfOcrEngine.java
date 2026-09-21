package com.auto.secop.ocr;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * OCR via Tess4J (preferred) or the {@code tesseract} CLI. Requires a local Tesseract install
 * and {@code spa} traineddata; unit tests mock this interface so CI does not need Tesseract.
 */
@Component
public class TesseractPdfOcrEngine implements PdfOcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractPdfOcrEngine.class);
    static final String LANGUAGES = "spa+eng";
    private static final String LANGUAGES_SPA_ONLY = "spa";
    private static final long PAGE_TIMEOUT_SECONDS = 120;
    private static final List<Path> TESSDATA_CANDIDATES = List.of(
            Path.of("/usr/share/tesseract-ocr/5/tessdata"),
            Path.of("/usr/share/tesseract-ocr/4.00/tessdata"),
            Path.of("/usr/share/tesseract-ocr/tessdata"),
            Path.of("/usr/share/tessdata"),
            Path.of("/opt/homebrew/share/tessdata"),
            Path.of("/usr/local/share/tessdata")
    );

    @Override
    public boolean available() {
        return tessdataDir() != null || cliWorks();
    }

    @Override
    public String recognize(List<BufferedImage> pages) throws IOException {
        if (pages == null || pages.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            BufferedImage page = pages.get(i);
            if (page == null) {
                continue;
            }
            String pageText = ocrPage(page);
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(pageText == null ? "" : pageText.strip());
        }
        return text.toString();
    }

    private String ocrPage(BufferedImage page) throws IOException {
        IOException tess4jFailure = null;
        try {
            return ocrWithTess4j(page);
        } catch (IOException ex) {
            tess4jFailure = ex;
            log.debug("Tess4J OCR failed, trying tesseract CLI: {}", ex.getMessage());
        }
        try {
            return ocrWithCli(page);
        } catch (IOException cliFailure) {
            if (tess4jFailure != null) {
                cliFailure.addSuppressed(tess4jFailure);
            }
            throw cliFailure;
        }
    }

    private String ocrWithTess4j(BufferedImage page) throws IOException {
        ITesseract tesseract = new Tesseract();
        Path tessdata = tessdataDir();
        if (tessdata != null) {
            tesseract.setDatapath(tessdata.toString());
        }
        tesseract.setLanguage(languageForTessdata(tessdata));
        tesseract.setPageSegMode(1);
        try {
            String text = tesseract.doOCR(page);
            return text == null ? "" : text;
        } catch (TesseractException | UnsatisfiedLinkError | RuntimeException ex) {
            throw new IOException("Tess4J OCR failed: " + ex.getMessage(), ex);
        }
    }

    private String ocrWithCli(BufferedImage page) throws IOException {
        Path tmp = Files.createTempFile("estudio-previo-ocr-", ".png");
        try {
            ImageIO.write(page, "png", tmp.toFile());
            String langs = cliLanguages();
            ProcessBuilder builder = new ProcessBuilder("tesseract", tmp.toString(), "stdout", "-l", langs);
            builder.redirectErrorStream(false);
            Process process = builder.start();
            String stdout;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                stdout = reader.lines().reduce((a, b) -> a + "\n" + b).orElse("");
            }
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished;
            try {
                finished = process.waitFor(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IOException("Tesseract CLI interrupted", ex);
            }
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Tesseract CLI timed out after " + PAGE_TIMEOUT_SECONDS + "s");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Tesseract CLI exited " + process.exitValue() + ": " + stderr.strip());
            }
            return stdout;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String languageForTessdata(Path tessdata) {
        if (tessdata != null && Files.isRegularFile(tessdata.resolve("eng.traineddata"))) {
            return LANGUAGES;
        }
        return LANGUAGES_SPA_ONLY;
    }

    private static String cliLanguages() {
        Path tessdata = tessdataDir();
        return languageForTessdata(tessdata);
    }

    static Path tessdataDir() {
        String prefix = System.getenv("TESSDATA_PREFIX");
        if (prefix != null && !prefix.isBlank()) {
            Path fromEnv = Path.of(prefix.trim());
            if (hasSpa(fromEnv)) {
                return fromEnv;
            }
            Path nested = fromEnv.resolve("tessdata");
            if (hasSpa(nested)) {
                return nested;
            }
        }
        for (Path candidate : TESSDATA_CANDIDATES) {
            if (hasSpa(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasSpa(Path tessdata) {
        return Files.isDirectory(tessdata)
                && Files.isRegularFile(tessdata.resolve("spa.traineddata"));
    }

    private static boolean cliWorks() {
        try {
            Process process = new ProcessBuilder("tesseract", "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    static boolean tesseractOnPath() {
        return cliWorks();
    }

    public static boolean tesseractWithSpanishData() {
        if (tessdataDir() != null) {
            return true;
        }
        if (!cliWorks()) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("tesseract", "--list-langs")
                    .redirectErrorStream(true)
                    .start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0 && out.contains("spa");
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
