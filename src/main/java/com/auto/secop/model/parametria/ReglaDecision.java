package com.auto.secop.model.parametria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReglaDecision(
        String id,
        String descripcion,
        String accionSiCumple,
        String accionSiNo
) {
}
