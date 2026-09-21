package com.auto.secop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SecopProcess(
        @JsonProperty("id_del_proceso") String idDelProceso,
        @JsonProperty("referencia_del_proceso") String referenciaDelProceso,
        @JsonProperty("entidad") String entidad,
        @JsonProperty("departamento_entidad") String departamentoEntidad,
        @JsonProperty("ciudad_entidad") String ciudadEntidad,
        @JsonProperty("ordenentidad") String ordenEntidad,
        @JsonProperty("nombre_del_procedimiento") String nombreDelProcedimiento,
        @JsonProperty("descripci_n_del_procedimiento") String descripcionDelProcedimiento,
        @JsonProperty("fase") String fase,
        @JsonProperty("precio_base") String precioBase,
        @JsonProperty("modalidad_de_contratacion") String modalidadDeContratacion,
        @JsonProperty("fecha_de_recepcion_de") String fechaDeRecepcionDe,
        @JsonProperty("estado_del_procedimiento") String estadoDelProcedimiento,
        @JsonProperty("estado_de_apertura_del_proceso") String estadoDeAperturaDelProceso,
        @JsonProperty("codigo_principal_de_categoria") String codigoPrincipalDeCategoria,
        @JsonProperty("categorias_adicionales") String categoriasAdicionales,
        @JsonProperty("urlproceso") UrlProceso urlproceso
) {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    public String processNumber() {
        if (referenciaDelProceso != null && !referenciaDelProceso.isBlank()) {
            return referenciaDelProceso.trim();
        }
        return idDelProceso;
    }

    public String url() {
        return urlproceso == null ? null : urlproceso.url();
    }

    public Optional<Long> parsedBudget() {
        if (precioBase == null || precioBase.isBlank()) {
            return Optional.empty();
        }
        try {
            BigDecimal value = new BigDecimal(precioBase.trim());
            if (value.signum() <= 0) {
                return Optional.empty();
            }
            return Optional.of(value.longValue());
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public Optional<LocalDate> parsedClosingDate() {
        if (fechaDeRecepcionDe == null || fechaDeRecepcionDe.isBlank()) {
            return Optional.empty();
        }
        String raw = fechaDeRecepcionDe.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return Optional.of(LocalDateTime.parse(raw, formatter).toLocalDate());
            } catch (DateTimeParseException ignored) {
                // try next
            }
            try {
                return Optional.of(LocalDate.parse(raw, formatter));
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return Optional.empty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UrlProceso(String url) {
    }
}
