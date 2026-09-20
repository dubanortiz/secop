package com.auto.secop.service;

import com.auto.secop.client.DatosGovSecopClient;
import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.ProcessMatchResponse;
import com.auto.secop.model.SecopProcess;
import com.auto.secop.model.parametria.ParametriaProfile;
import com.auto.secop.support.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class ProcessSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProcessSearchService.class);

    private final DatosGovSecopClient client;
    private final ProcessMatchScorer scorer;
    private final ParametriaProfile profile;
    private final SecopProperties properties;

    public ProcessSearchService(
            DatosGovSecopClient client,
            ProcessMatchScorer scorer,
            ParametriaProfile profile,
            SecopProperties properties
    ) {
        this.client = client;
        this.scorer = scorer;
        this.profile = profile;
        this.properties = properties;
    }

    public List<ProcessMatchResponse> search(Integer minMatch, String departamento, Integer limit) {
        int threshold = minMatch == null ? properties.safeDefaultMinMatch() : minMatch;
        int maxResults = limit == null ? properties.safeDefaultLimit() : limit;
        validate(threshold, maxResults);

        List<SecopProcess> raw = client.findOpenProcesses(profile, departamento);
        List<ProcessMatchResponse> matches = raw.stream()
                .filter(process -> !isExcludedEntity(process))
                .map(process -> toMatch(process, threshold, departamento))
                .filter(match -> match != null)
                .sorted(Comparator.comparingInt(ProcessMatchResponse::matchPercent).reversed()
                        .thenComparing(ProcessMatchResponse::processNumber, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(maxResults)
                .toList();
        log.info("Scored {} SECOP processes, returning {} with match >= {}", raw.size(), matches.size(), threshold);
        return matches;
    }

    private ProcessMatchResponse toMatch(SecopProcess process, int threshold, String departamento) {
        int percent = scorer.score(process, profile, departamento);
        if (percent < threshold) {
            return null;
        }
        return new ProcessMatchResponse(
                process.processNumber(),
                percent,
                process.entidad(),
                process.nombreDelProcedimiento(),
                process.modalidadDeContratacion(),
                process.parsedBudget().orElse(null),
                process.parsedClosingDate().map(date -> date.toString()).orElse(null),
                process.url()
        );
    }

    private boolean isExcludedEntity(SecopProcess process) {
        String entity = TextNormalizer.normalize(process.entidad());
        return profile.filtros().safeEntidadesExcluir().stream()
                .map(TextNormalizer::normalize)
                .anyMatch(excluded -> !excluded.isBlank() && entity.contains(excluded));
    }

    private static void validate(int minMatch, int limit) {
        if (minMatch < 0 || minMatch > 100) {
            throw new IllegalArgumentException("minMatch must be between 0 and 100");
        }
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
    }
}
