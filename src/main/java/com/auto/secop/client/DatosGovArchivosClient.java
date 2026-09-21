package com.auto.secop.client;

import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.SecopArchivo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class DatosGovArchivosClient {

    private static final Logger log = LoggerFactory.getLogger(DatosGovArchivosClient.class);
    static final String SELECT_FIELDS = String.join(",",
            "id_documento",
            "proceso",
            "nombre_archivo",
            "tamanno_archivo",
            "extensi_n",
            "descripci_n",
            "url_descarga_documento"
    );
    private static final ParameterizedTypeReference<List<SecopArchivo>> ARCHIVO_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final SecopProperties properties;

    public DatosGovArchivosClient(RestClient archivosGovRestClient, SecopProperties properties) {
        this.restClient = archivosGovRestClient;
        this.properties = properties;
    }

    public List<SecopArchivo> findByPortfolio(String portfolioId) {
        if (portfolioId == null || portfolioId.isBlank()) {
            return List.of();
        }
        String where = "proceso='" + SoqlQueryBuilder.escape(portfolioId.trim()) + "'";
        URI uri = UriComponentsBuilder.fromUriString(properties.safeArchivosGovBaseUrl())
                .queryParam("$select", SELECT_FIELDS)
                .queryParam("$where", where)
                .queryParam("$limit", 100)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        log.info("Querying datos.gov.co SECOP II archivos for proceso={}", portfolioId);
        try {
            List<SecopArchivo> files = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(ARCHIVO_LIST);
            return files == null ? List.of() : files;
        } catch (RestClientException ex) {
            throw new DatosGovUnavailableException("Unable to query datos.gov.co SECOP II archivos dataset", ex);
        }
    }
}
