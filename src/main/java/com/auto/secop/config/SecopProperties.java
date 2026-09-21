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
        List<String> nationalEntities,
        @Name("estudios-previos-dir") String estudiosPreviosDir,
        String archivosGovBaseUrl
) {

    public static final String DEFAULT_ESTUDIOS_PREVIOS_DIR = "src/main/resources/estudios-previos";
    public static final String DEFAULT_ARCHIVOS_GOV_BASE_URL =
            "https://www.datos.gov.co/resource/dmgg-8hin.json";

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

    /**
     * Local {@code spring-boot:run} default is the resources folder. Packaging a JAR
     * cannot write into the classpath — production must set an absolute writable path.
     */
    public String safeEstudiosPreviosDir() {
        return estudiosPreviosDir == null || estudiosPreviosDir.isBlank()
                ? DEFAULT_ESTUDIOS_PREVIOS_DIR
                : estudiosPreviosDir;
    }

    public String safeArchivosGovBaseUrl() {
        return archivosGovBaseUrl == null || archivosGovBaseUrl.isBlank()
                ? DEFAULT_ARCHIVOS_GOV_BASE_URL
                : archivosGovBaseUrl;
    }
}
