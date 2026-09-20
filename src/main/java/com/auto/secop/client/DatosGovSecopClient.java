package com.auto.secop.client;

import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.SecopProcess;
import com.auto.secop.model.parametria.ParametriaProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class DatosGovSecopClient {

    private static final Logger log = LoggerFactory.getLogger(DatosGovSecopClient.class);
    private static final ParameterizedTypeReference<List<SecopProcess>> PROCESS_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final SecopProperties properties;

    public DatosGovSecopClient(RestClient datosGovRestClient, SecopProperties properties) {
        this.restClient = datosGovRestClient;
        this.properties = properties;
    }

    public List<SecopProcess> findOpenProcesses(ParametriaProfile profile, String departamento) {
        String where = SoqlQueryBuilder.buildWhere(profile, departamento, properties.nationalEntities());
        int limit = properties.fetchLimit();
        log.info("Querying datos.gov.co SECOP II with $limit={} $where={}", limit, where);
        try {
            List<SecopProcess> processes = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("$select", SoqlQueryBuilder.SELECT_FIELDS)
                            .queryParam("$where", where)
                            .queryParam("$order", "fecha_de_recepcion_de DESC")
                            .queryParam("$limit", limit)
                            .build())
                    .retrieve()
                    .body(PROCESS_LIST);
            return processes == null ? List.of() : processes;
        } catch (RestClientException ex) {
            throw new DatosGovUnavailableException("Unable to query datos.gov.co SECOP II dataset", ex);
        }
    }
}
