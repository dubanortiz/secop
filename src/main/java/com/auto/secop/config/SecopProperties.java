package com.auto.secop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

import java.util.List;

@ConfigurationProperties(prefix = "secop")
public record SecopProperties(
        String datosGovBaseUrl,
        @Name("parametria-path") String profilePath,
        int defaultMinMatch,
        int fetchLimit,
        int defaultLimit,
        List<String> nationalEntities
) {

    public List<String> nationalEntitiesOrEmpty() {
        return nationalEntities == null ? List.of() : nationalEntities;
    }

    public int safeFetchLimit() {
        return fetchLimit > 0 ? fetchLimit : 200;
    }

    public int safeDefaultLimit() {
        return defaultLimit > 0 ? defaultLimit : 20;
    }

    public int safeDefaultMinMatch() {
        return defaultMinMatch > 0 ? defaultMinMatch : 80;
    }
}
