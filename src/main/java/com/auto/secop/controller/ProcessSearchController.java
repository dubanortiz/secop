package com.auto.secop.controller;

import com.auto.secop.model.ProcessMatchResponse;
import com.auto.secop.service.ProcessSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/processes")
public class ProcessSearchController {

    private final ProcessSearchService processSearchService;

    public ProcessSearchController(ProcessSearchService processSearchService) {
        this.processSearchService = processSearchService;
    }

    @GetMapping("/search")
    public List<ProcessMatchResponse> search(
            @RequestParam(required = false) Integer minMatch,
            @RequestParam(required = false) String departamento,
            @RequestParam(required = false) Integer limit
    ) {
        return processSearchService.search(minMatch, departamento, limit);
    }
}
