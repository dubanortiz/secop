package com.auto.secop.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EstudioPrevioDownloadResult(
        String processNumber,
        EstudioPrevioDownloadStatus status,
        List<SavedFile> files,
        String error
) {

    public static EstudioPrevioDownloadResult of(
            String processNumber,
            EstudioPrevioDownloadStatus status,
            List<SavedFile> files,
            String error
    ) {
        return new EstudioPrevioDownloadResult(
                processNumber,
                status,
                files == null ? List.of() : List.copyOf(files),
                error
        );
    }

    public record SavedFile(String fileName, String path, long sizeBytes) {
    }
}
