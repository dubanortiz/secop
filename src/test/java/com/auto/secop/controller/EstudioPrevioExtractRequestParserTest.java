package com.auto.secop.controller;

import com.auto.secop.model.ProcessMatchResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EstudioPrevioExtractRequestParserTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void parsesSearchArrayLikeDownload() {
        List<ProcessMatchResponse> parsed = EstudioPrevioExtractRequestParser.parse(
                mapper.readTree("[{\"processNumber\":\"A-1\",\"matchPercent\":80}]"), mapper);

        assertThat(parsed).extracting(ProcessMatchResponse::processNumber).containsExactly("A-1");
        assertThat(parsed.getFirst().matchPercent()).isEqualTo(80);
    }

    @Test
    void parsesProcessNumberStringsAndFolderPaths() {
        List<ProcessMatchResponse> fromArray = EstudioPrevioExtractRequestParser.parse(
                mapper.readTree("[\"MC-2026-047\",\"src/main/resources/estudios-previos/129-CMC-2026\"]"), mapper);
        List<ProcessMatchResponse> fromWrapper = EstudioPrevioExtractRequestParser.parse(
                mapper.readTree("{\"processNumbers\":[\"X-1\"]}"), mapper);

        assertThat(fromArray).extracting(ProcessMatchResponse::processNumber)
                .containsExactly("MC-2026-047", "129-CMC-2026");
        assertThat(fromWrapper).extracting(ProcessMatchResponse::processNumber).containsExactly("X-1");
    }
}
