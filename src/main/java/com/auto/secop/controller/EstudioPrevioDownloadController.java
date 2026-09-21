package com.auto.secop.controller;

import com.auto.secop.model.EstudioPrevioDownloadResult;
import com.auto.secop.model.ProcessMatchResponse;
import com.auto.secop.service.EstudioPrevioDownloadService;
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
public class EstudioPrevioDownloadController {

    private final EstudioPrevioDownloadService service;
    private final ObjectMapper objectMapper;

    public EstudioPrevioDownloadController(EstudioPrevioDownloadService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/estudios-previos/download", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<EstudioPrevioDownloadResult> download(@RequestBody JsonNode body) {
        List<ProcessMatchResponse> processes = EstudioPrevioDownloadRequestParser.parse(body, objectMapper);
        return service.download(processes);
    }
}
