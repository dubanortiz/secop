package com.auto.secop.model.parametria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Proveedor(
        String razonSocial,
        String tipoPersona,
        String departamento,
        String entidadContratanteFrecuente
) {
}
