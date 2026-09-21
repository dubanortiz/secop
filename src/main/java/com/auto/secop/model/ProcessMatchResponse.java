package com.auto.secop.model;

public record ProcessMatchResponse(
        String processNumber,
        int matchPercent,
        String entity,
        String object,
        String modality,
        Long budget,
        String closingDate,
        String url
) {
}
