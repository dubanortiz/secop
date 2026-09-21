package com.auto.secop.client;

import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.ProcessPortfolio;
import com.auto.secop.model.SecopProcess;
import com.auto.secop.model.parametria.ParametriaProfile;
import com.auto.secop.support.SafePaths;
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
import java.util.Optional;

@Component
public class DatosGovSecopClient {

    private static final Logger log = LoggerFactory.getLogger(DatosGovSecopClient.class);
    static final String PORTFOLIO_SELECT_FIELDS = String.join(",",
            "referencia_del_proceso",
            "id_del_portafolio",
            "id_del_proceso",
            "urlproceso"
    );
    private static final ParameterizedTypeReference<List<SecopProcess>> PROCESS_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<ProcessPortfolio>> PORTFOLIO_LIST =
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
        URI uri = UriComponentsBuilder.fromUriString(properties.datosGovBaseUrl())
                .queryParam("$select", SoqlQueryBuilder.SELECT_FIELDS)
                .queryParam("$where", where)
                .queryParam("$order", "fecha_de_recepcion_de DESC")
                .queryParam("$limit", limit)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        log.info("Querying datos.gov.co SECOP II with $limit={} $where={}", limit, where);
        try {
            List<SecopProcess> processes = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(PROCESS_LIST);
            return processes == null ? List.of() : processes;
        } catch (RestClientException ex) {
            throw new DatosGovUnavailableException("Unable to query datos.gov.co SECOP II dataset", ex);
        }
    }

    /**
     * Resolves {@code id_del_portafolio} (CO1.BDOS.*) so attachments can be listed on dmgg-8hin.
     * Lookup is by process reference first, then by the exact OpportunityDetail URL.
     */
    public Optional<ProcessPortfolio> findPortfolio(String processNumber, String url) {
        if (processNumber != null && !processNumber.isBlank()) {
            Optional<ProcessPortfolio> byNumber = firstPortfolio(
                    queryPortfolios("referencia_del_proceso='" + SoqlQueryBuilder.escape(processNumber.trim()) + "'")
            );
            if (byNumber.isPresent()) {
                return byNumber;
            }
        }
        String canonical = SafePaths.canonicalOpportunityUrl(url);
        if (canonical != null && !canonical.isBlank()) {
            String where = "urlproceso='" + SoqlQueryBuilder.escape(canonical) + "'"
                    + " OR urlproceso.url='" + SoqlQueryBuilder.escape(canonical) + "'";
            if (url != null && !url.isBlank() && !url.trim().equals(canonical)) {
                where += " OR urlproceso='" + SoqlQueryBuilder.escape(url.trim()) + "'";
            }
            return firstPortfolio(queryPortfolios("(" + where + ")"));
        }
        return Optional.empty();
    }

    private List<ProcessPortfolio> queryPortfolios(String where) {
        URI uri = UriComponentsBuilder.fromUriString(properties.datosGovBaseUrl())
                .queryParam("$select", PORTFOLIO_SELECT_FIELDS)
                .queryParam("$where", where)
                .queryParam("$limit", 5)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        try {
            List<ProcessPortfolio> rows = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(PORTFOLIO_LIST);
            return rows == null ? List.of() : rows;
        } catch (RestClientException ex) {
            throw new DatosGovUnavailableException("Unable to look up SECOP process portfolio on datos.gov.co", ex);
        }
    }

    private static Optional<ProcessPortfolio> firstPortfolio(List<ProcessPortfolio> rows) {
        return rows.stream()
                .filter(ProcessPortfolio::hasPortfolioId)
                .findFirst();
    }
}
