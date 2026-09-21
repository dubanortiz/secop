package com.auto.secop.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EstudioPrevioExtractResult(
        String processNumber,
        EstudioPrevioExtractStatus status,
        List<ExtractedFile> files,
        String error
) {

    public static EstudioPrevioExtractResult of(
            String processNumber,
            EstudioPrevioExtractStatus status,
            List<ExtractedFile> files,
            String error
    ) {
        return new EstudioPrevioExtractResult(
                processNumber,
                status,
                files == null ? List.of() : List.copyOf(files),
                error
        );
    }

    public record ExtractedFile(String pdf, String textFile, EstudioPrevioExtractMethod method, int chars) {
    }
}
