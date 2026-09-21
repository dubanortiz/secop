package com.auto.secop.controller;

import com.auto.secop.model.ProcessMatchResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Accepts the same body as download (search-result array or {@code {"processes":[...]}})
 * plus a process-number / folder-path shortcut: a JSON string array or
 * {@code {"processNumbers":[...]}}.
 */
public final class EstudioPrevioExtractRequestParser {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private EstudioPrevioExtractRequestParser() {
    }

    public static List<ProcessMatchResponse> parse(JsonNode body, ObjectMapper mapper) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (isStringArray(body)) {
            return fromProcessNumbers(readStringList(body, mapper));
        }
        if (body.isObject() && body.has("processNumbers") && !body.has("processes")) {
            return fromProcessNumbers(readStringList(body.get("processNumbers"), mapper));
        }
        return EstudioPrevioDownloadRequestParser.parse(body, mapper);
    }

    private static boolean isStringArray(JsonNode body) {
        if (!body.isArray() || body.isEmpty()) {
            return false;
        }
        for (JsonNode node : body) {
            if (!node.isTextual()) {
                return false;
            }
        }
        return true;
    }

    private static List<String> readStringList(JsonNode node, ObjectMapper mapper) {
        try {
            List<String> values = mapper.convertValue(node, STRING_LIST);
            return values == null ? List.of() : values;
        } catch (IllegalArgumentException | JacksonException ex) {
            throw new IllegalArgumentException("Unable to parse process number list", ex);
        }
    }

    private static List<ProcessMatchResponse> fromProcessNumbers(List<String> raw) {
        List<ProcessMatchResponse> processes = new ArrayList<>();
        for (String value : raw) {
            String processNumber = processNumberFrom(value);
            processes.add(new ProcessMatchResponse(processNumber, 0, null, null, null, null, null, null));
        }
        if (processes.size() > 200) {
            throw new IllegalArgumentException("At most 200 processes can be extracted per request");
        }
        return List.copyOf(processes);
    }

    /**
     * {@code MC-2026-047} or a path whose last segment is the process folder
     * ({@code estudios-previos/MC-2026-047}).
     */
    static String processNumberFrom(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim().replace('\\', '/');
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        Path path = Path.of(trimmed);
        Path fileName = path.getFileName();
        return fileName == null ? trimmed : fileName.toString();
    }
}
