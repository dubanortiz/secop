package com.auto.secop.model.parametria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExperienciaEspecifica(List<String> objetosContractualesAceptados) {

    public List<String> safeObjetos() {
        return objetosContractualesAceptados == null ? List.of() : objetosContractualesAceptados;
    }
}
