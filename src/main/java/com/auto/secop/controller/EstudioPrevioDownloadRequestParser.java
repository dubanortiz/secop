package com.auto.secop.controller;

import com.auto.secop.model.ProcessMatchResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Accepts either the raw search-result array or {@code {"processes":[...]}}.
 */
public final class EstudioPrevioDownloadRequestParser {

    private static final int MAX_PROCESSES = 200;
    private static final TypeReference<List<ProcessMatchResponse>> PROCESS_LIST = new TypeReference<>() {
    };

    private EstudioPrevioDownloadRequestParser() {
    }

    public static List<ProcessMatchResponse> parse(JsonNode body, ObjectMapper mapper) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            throw new IllegalArgumentException("Request body is required");
        }
        try {
            List<ProcessMatchResponse> processes;
            if (body.isArray()) {
                processes = mapper.convertValue(body, PROCESS_LIST);
            } else if (body.isObject() && body.has("processes")) {
                processes = mapper.convertValue(body.get("processes"), PROCESS_LIST);
            } else {
                throw new IllegalArgumentException(
                        "Body must be a JSON array of ProcessMatchResponse or {\"processes\": [...]}");
            }
            if (processes == null) {
                return List.of();
            }
            if (processes.size() > MAX_PROCESSES) {
                throw new IllegalArgumentException("At most " + MAX_PROCESSES + " processes can be downloaded per request");
            }
            return List.copyOf(processes);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Unable to parse ProcessMatchResponse list", ex);
        }
    }
}
