package com.auto.secop.model.parametria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatronesDetectados(List<String> perfilNegocio) {

    public List<String> safePerfilNegocio() {
        return perfilNegocio == null ? List.of() : perfilNegocio;
    }
}
