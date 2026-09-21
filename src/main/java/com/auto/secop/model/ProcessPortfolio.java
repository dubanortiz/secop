package com.auto.secop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessPortfolio(
        @JsonProperty("referencia_del_proceso") String processNumber,
        @JsonProperty("id_del_portafolio") String portfolioId,
        @JsonProperty("id_del_proceso") String processId,
        @JsonProperty("urlproceso") SecopProcess.UrlProceso urlproceso
) {

    public String url() {
        return urlproceso == null ? null : urlproceso.url();
    }

    public boolean hasPortfolioId() {
        return portfolioId != null && !portfolioId.isBlank();
    }
}
