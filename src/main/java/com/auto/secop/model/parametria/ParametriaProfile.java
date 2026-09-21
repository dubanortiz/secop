package com.auto.secop.model.parametria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ParametriaProfile(
        String version,
        String plataforma,
        Proveedor proveedor,
        FiltrosBusqueda filtrosBusqueda,
        CriteriosElegibilidad criteriosElegibilidad,
        List<ReglaDecision> reglasDecision,
        AprendizajeDePropuestas aprendizajeDePropuestas
) {

    public FiltrosBusqueda filtros() {
        return filtrosBusqueda == null ? FiltrosBusqueda.empty() : filtrosBusqueda;
    }
}
