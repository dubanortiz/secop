package com.auto.secop.controller;

import com.auto.secop.model.ProcessMatchResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstudioPrevioDownloadRequestParserTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void parsesArrayAndWrapper() {
        List<ProcessMatchResponse> fromArray = EstudioPrevioDownloadRequestParser.parse(
                mapper.readTree("[{\"processNumber\":\"A-1\",\"matchPercent\":80}]"), mapper);
        List<ProcessMatchResponse> fromWrapper = EstudioPrevioDownloadRequestParser.parse(
                mapper.readTree("{\"processes\":[{\"processNumber\":\"A-1\",\"matchPercent\":80}]}"), mapper);

        assertThat(fromArray).extracting(ProcessMatchResponse::processNumber).containsExactly("A-1");
        assertThat(fromWrapper).extracting(ProcessMatchResponse::processNumber).containsExactly("A-1");
    }

    @Test
    void rejectsUnknownObjectShape() {
        assertThatThrownBy(() -> EstudioPrevioDownloadRequestParser.parse(mapper.readTree("{\"foo\":[]}"), mapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processes");
    }
}
