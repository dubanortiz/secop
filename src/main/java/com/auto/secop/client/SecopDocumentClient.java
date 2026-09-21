package com.auto.secop.client;

import com.auto.secop.config.RestClientConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only fetch of public SECOP attachment bytes. Detects reCAPTCHA HTML instead of inventing files.
 */
@Component
public class SecopDocumentClient {

    private static final Logger log = LoggerFactory.getLogger(SecopDocumentClient.class);
    private static final Pattern FILENAME = Pattern.compile("filename\\*?=(?:UTF-8''|\"?)([^\";]+)", Pattern.CASE_INSENSITIVE);
    static final int MAX_BYTES = 50 * 1024 * 1024;

    private final RestClient restClient;

    public SecopDocumentClient(RestClient secopDocumentRestClient) {
        this.restClient = secopDocumentRestClient;
    }

    public FetchedDocument download(String url) {
        if (url == null || url.isBlank()) {
            return FetchedDocument.error("Missing download URL");
        }
        URI uri = URI.create(url.trim());
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.USER_AGENT, RestClientConfiguration.USER_AGENT)
                    .exchange((request, response) -> {
                        byte[] body = response.getBody().readAllBytes();
                        if (body.length > MAX_BYTES) {
                            return FetchedDocument.error("Document exceeds " + MAX_BYTES + " bytes");
                        }
                        MediaType contentType = response.getHeaders().getContentType();
                        String fileName = fileNameFrom(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
                        int status = response.getStatusCode().value();
                        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                        if (isBlocked(status, location, contentType, body)) {
                            log.info("SECOP document download blocked by captcha/login for {}", uri);
                            return FetchedDocument.blocked(
                                    "SECOP returned a reCAPTCHA/login page instead of the document; a human must complete captcha"
                            );
                        }
                        if (status >= 400) {
                            return FetchedDocument.error("HTTP " + status + " downloading document");
                        }
                        return FetchedDocument.ok(body, fileName, contentType == null ? null : contentType.toString());
                    });
        } catch (RestClientException ex) {
            return FetchedDocument.error("Unable to download document: " + ex.getMessage());
        }
    }

    public Optional<String> probeBlockedPage(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        FetchedDocument fetched = download(url);
        if (fetched.blocked()) {
            return Optional.of(fetched.detail());
        }
        return Optional.empty();
    }

    static boolean isBlocked(int status, String location, MediaType contentType, byte[] body) {
        if (location != null && location.toLowerCase(Locale.ROOT).contains("recaptcha")) {
            return true;
        }
        if (status >= 300 && status < 400 && location != null
                && location.toLowerCase(Locale.ROOT).contains("login")) {
            return true;
        }
        boolean html = contentType != null && MediaType.TEXT_HTML.isCompatibleWith(contentType);
        String text = preview(body);
        if (html || looksLikeHtml(body)) {
            String lower = text.toLowerCase(Locale.ROOT);
            return lower.contains("recaptcha")
                    || lower.contains("googlerecaptcha")
                    || lower.contains("complete a valida")
                    || lower.contains("por favor complete");
        }
        return false;
    }

    static String fileNameFrom(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isBlank()) {
            return null;
        }
        Matcher matcher = FILENAME.matcher(contentDisposition);
        if (matcher.find()) {
            return matcher.group(1).replace("\"", "").trim();
        }
        return null;
    }

    private static boolean looksLikeHtml(byte[] body) {
        String preview = preview(body).trim().toLowerCase(Locale.ROOT);
        return preview.startsWith("<!doctype html") || preview.startsWith("<html");
    }

    private static String preview(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        int len = Math.min(body.length, 8000);
        return new String(body, 0, len, StandardCharsets.UTF_8);
    }

    public record FetchedDocument(
            byte[] content,
            String fileName,
            String contentType,
            boolean blocked,
            boolean error,
            String detail
    ) {
        public static FetchedDocument ok(byte[] content, String fileName, String contentType) {
            return new FetchedDocument(content, fileName, contentType, false, false, null);
        }

        public static FetchedDocument blocked(String detail) {
            return new FetchedDocument(null, null, null, true, false, detail);
        }

        public static FetchedDocument error(String detail) {
            return new FetchedDocument(null, null, null, false, true, detail);
        }

        public ByteArrayInputStream inputStream() throws IOException {
            if (content == null) {
                throw new IOException("No document content");
            }
            return new ByteArrayInputStream(content);
        }
    }
}
