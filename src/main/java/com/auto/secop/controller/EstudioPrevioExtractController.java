package com.auto.secop.controller;

import com.auto.secop.model.EstudioPrevioExtractResult;
import com.auto.secop.model.ProcessMatchResponse;
import com.auto.secop.service.EstudioPrevioExtractService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@RestController
@RequestMapping("/api/v1/processes")
public class EstudioPrevioExtractController {

    private final EstudioPrevioExtractService service;
    private final ObjectMapper objectMapper;

    public EstudioPrevioExtractController(EstudioPrevioExtractService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/estudios-previos/extract", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<EstudioPrevioExtractResult> extract(@RequestBody JsonNode body) {
        List<ProcessMatchResponse> processes = EstudioPrevioExtractRequestParser.parse(body, objectMapper);
        return service.extract(processes);
    }
}
