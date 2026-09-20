package com.auto.secop.model.parametria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FiltrosBusqueda(
        List<String> modalidades,
        List<String> estadosProceso,
        List<String> codigosUnspsc,
        List<String> entidadesInteres,
        List<String> entidadesExcluir,
        List<String> departamentos,
        List<String> municipios,
        Long presupuestoMinCop,
        Long presupuestoMaxCop,
        Integer diasMinimosAntesCierre,
        List<String> palabrasClaveIncluir,
        List<String> palabrasClaveExcluir
) {

    public static FiltrosBusqueda empty() {
        return new FiltrosBusqueda(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null, null, null, List.of(), List.of()
        );
    }

    public List<String> safeModalidades() {
        return modalidades == null ? List.of() : modalidades;
    }

    public List<String> safeEstadosProceso() {
        return estadosProceso == null ? List.of() : estadosProceso;
    }

    public List<String> safeCodigosUnspsc() {
        return codigosUnspsc == null ? List.of() : codigosUnspsc;
    }

    public List<String> safeEntidadesInteres() {
        return entidadesInteres == null ? List.of() : entidadesInteres;
    }

    public List<String> safeEntidadesExcluir() {
        return entidadesExcluir == null ? List.of() : entidadesExcluir;
    }

    public List<String> safeDepartamentos() {
        return departamentos == null ? List.of() : departamentos;
    }

    public List<String> safeMunicipios() {
        return municipios == null ? List.of() : municipios;
    }

    public List<String> safePalabrasClaveIncluir() {
        return palabrasClaveIncluir == null ? List.of() : palabrasClaveIncluir;
    }

    public List<String> safePalabrasClaveExcluir() {
        return palabrasClaveExcluir == null ? List.of() : palabrasClaveExcluir;
    }
}
