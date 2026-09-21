package com.auto.secop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SecopArchivo(
        @JsonProperty("id_documento") String idDocumento,
        @JsonProperty("proceso") String proceso,
        @JsonProperty("nombre_archivo") String nombreArchivo,
        @JsonProperty("tamanno_archivo") String tamannoArchivo,
        @JsonProperty("extensi_n") String extension,
        @JsonProperty("descripci_n") String descripcion,
        @JsonProperty("url_descarga_documento") Url urlDescarga
) {

    public static final String RETRIEVE_FILE_URL =
            "https://community.secop.gov.co/Public/Archive/RetrieveFile/Index?DocumentId=%s"
                    + "&InCommunity=False&InPaymentGateway=False&DocUniqueIdentifier=";

    public String downloadUrl() {
        if (urlDescarga != null && urlDescarga.url() != null && !urlDescarga.url().isBlank()) {
            return urlDescarga.url();
        }
        if (idDocumento == null || idDocumento.isBlank()) {
            return null;
        }
        return RETRIEVE_FILE_URL.formatted(idDocumento.trim());
    }

    public String displayName() {
        if (nombreArchivo != null && !nombreArchivo.isBlank()) {
            return nombreArchivo;
        }
        if (descripcion != null && !descripcion.isBlank()) {
            return descripcion;
        }
        return "estudio-previo.pdf";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Url(String url) {
    }
}
