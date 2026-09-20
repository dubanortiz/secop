package com.auto.secop.service;

import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.SecopProcess;
import com.auto.secop.model.parametria.CriteriosElegibilidad;
import com.auto.secop.model.parametria.ExperienciaEspecifica;
import com.auto.secop.model.parametria.FiltrosBusqueda;
import com.auto.secop.model.parametria.ParametriaProfile;
import com.auto.secop.model.parametria.PatronesDetectados;
import com.auto.secop.support.TextNormalizer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Transparent profile scorer against parametría v2.0.1 (not a pliego parser).
 *
 * <p>Weights follow {@code reglas_decision} plus the search profile:
 * <ul>
 *   <li>35 object / keywords / UNSPSC — {@code palabras_clave_incluir}, accepted objects, business profile</li>
 *   <li>25 entity / territory — Caquetá / Curillo-Florencia / entidades_interes OR national targets</li>
 *   <li>15 modality — Mínima cuantía or régimen especial (con ofertas)</li>
 *   <li>15 budget band — soft 30M–200M COP ({@code presupuesto_en_rango} is discard-if-far)</li>
 *   <li>10 time-to-close — prefer {@code dias_minimos_antes_cierre}; lower if closer</li>
 * </ul>
 * Missing fields are scored conservatively. ESAL aporte and garantía de seriedad are not
 * visible in datos.gov.co, so they are not guessed.
 */
@Component
public class ProcessMatchScorer {

    public static final int WEIGHT_OBJECT = 35;
    public static final int WEIGHT_TERRITORY = 25;
    public static final int WEIGHT_MODALITY = 15;
    public static final int WEIGHT_BUDGET = 15;
    public static final int WEIGHT_TIME = 10;

    private final Clock clock;
    private final SecopProperties properties;

    public ProcessMatchScorer(Clock clock, SecopProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public int score(SecopProcess process, ParametriaProfile profile, String departamentoOverride) {
        int total = scoreObject(process, profile)
                + scoreTerritory(process, profile, departamentoOverride)
                + scoreModality(process, profile)
                + scoreBudget(process, profile)
                + scoreTime(process, profile);
        return Math.min(100, Math.max(0, total));
    }

    int scoreObject(SecopProcess process, ParametriaProfile profile) {
        String haystack = TextNormalizer.squash(
                nullToEmpty(process.nombreDelProcedimiento()) + " " + nullToEmpty(process.descripcionDelProcedimiento())
        );
        FiltrosBusqueda filtros = profile.filtros();
        for (String excluded : filtros.safePalabrasClaveExcluir()) {
            if (TextNormalizer.containsPhrase(haystack, TextNormalizer.squash(excluded))) {
                return 0;
            }
        }

        Set<String> hits = new LinkedHashSet<>();
        for (String keyword : objectPhrases(profile)) {
            String phrase = TextNormalizer.squash(keyword);
            if (TextNormalizer.containsPhrase(haystack, phrase)) {
                hits.add(phrase);
            }
        }
        if (matchesUnspsc(process, filtros)) {
            hits.add("__unspsc__");
        }
        return objectPoints(hits.size());
    }

    static int objectPoints(int hits) {
        if (hits <= 0) {
            return 0;
        }
        if (hits == 1) {
            return 18;
        }
        if (hits == 2) {
            return 28;
        }
        return WEIGHT_OBJECT;
    }

    int scoreTerritory(SecopProcess process, ParametriaProfile profile, String departamentoOverride) {
        FiltrosBusqueda filtros = profile.filtros();
        int score = 0;
        String department = TextNormalizer.normalize(process.departamentoEntidad());
        String city = TextNormalizer.normalize(process.ciudadEntidad());
        String entity = TextNormalizer.normalize(process.entidad());

        List<String> departments = new ArrayList<>();
        if (departamentoOverride != null && !departamentoOverride.isBlank()) {
            departments.add(departamentoOverride);
        } else {
            departments.addAll(filtros.safeDepartamentos());
            if (profile.proveedor() != null && profile.proveedor().departamento() != null) {
                departments.add(profile.proveedor().departamento());
            }
        }
        boolean departmentMatch = departments.stream()
                .map(TextNormalizer::normalize)
                .anyMatch(expected -> !expected.isBlank() && department.contains(expected));
        if (departmentMatch) {
            score += 18;
        }

        boolean municipalityMatch = filtros.safeMunicipios().stream()
                .map(TextNormalizer::normalize)
                .anyMatch(expected -> !expected.isBlank() && (city.contains(expected) || entity.contains(expected)));
        if (municipalityMatch) {
            score += 4;
        }

        boolean entityOfInterest = filtros.safeEntidadesInteres().stream()
                .map(TextNormalizer::normalize)
                .anyMatch(expected -> !expected.isBlank() && entity.contains(expected));
        if (entityOfInterest) {
            score += 7;
        }

        if (isNationalTarget(entity)) {
            score += departmentMatch ? 7 : 15;
        }
        return Math.min(WEIGHT_TERRITORY, score);
    }

    int scoreModality(SecopProcess process, ParametriaProfile profile) {
        String modality = TextNormalizer.normalize(process.modalidadDeContratacion());
        boolean known = profile.filtros().safeModalidades().stream()
                .map(TextNormalizer::normalize)
                .anyMatch(expected -> !expected.isBlank() && modality.equals(expected));
        return known ? WEIGHT_MODALITY : 0;
    }

    int scoreBudget(SecopProcess process, ParametriaProfile profile) {
        Optional<Long> budget = process.parsedBudget();
        Long min = profile.filtros().presupuestoMinCop();
        Long max = profile.filtros().presupuestoMaxCop();
        if (min == null || max == null) {
            return 8;
        }
        if (budget.isEmpty()) {
            return 4;
        }
        long value = budget.get();
        if (value >= min && value <= max) {
            return WEIGHT_BUDGET;
        }
        long lowerSoft = Math.round(min * 0.75);
        long upperSoft = Math.round(max * 1.25);
        if (value >= lowerSoft && value <= upperSoft) {
            return 8;
        }
        return 0;
    }

    int scoreTime(SecopProcess process, ParametriaProfile profile) {
        Optional<LocalDate> closing = process.parsedClosingDate();
        if (closing.isEmpty()) {
            return 3;
        }
        int minDays = profile.filtros().diasMinimosAntesCierre() == null
                ? 2
                : profile.filtros().diasMinimosAntesCierre();
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of("America/Bogota"));
        long days = ChronoUnit.DAYS.between(today, closing.get());
        if (days >= minDays) {
            return WEIGHT_TIME;
        }
        if (days == minDays - 1) {
            return 5;
        }
        if (days >= 0) {
            return 2;
        }
        return 0;
    }

    private boolean isNationalTarget(String entityNormalized) {
        return properties.nationalEntitiesOrEmpty().stream()
                .map(TextNormalizer::normalize)
                .anyMatch(expected -> !expected.isBlank() && entityNormalized.contains(expected));
    }

    private static boolean matchesUnspsc(SecopProcess process, FiltrosBusqueda filtros) {
        String codes = TextNormalizer.normalize(
                nullToEmpty(process.codigoPrincipalDeCategoria()) + " " + nullToEmpty(process.categoriasAdicionales())
        );
        if (codes.isBlank()) {
            return false;
        }
        return filtros.safeCodigosUnspsc().stream()
                .map(TextNormalizer::normalize)
                .anyMatch(code -> !code.isBlank() && codes.contains(code));
    }

    private static List<String> objectPhrases(ParametriaProfile profile) {
        Set<String> phrases = new LinkedHashSet<>(profile.filtros().safePalabrasClaveIncluir());
        CriteriosElegibilidad criterios = profile.criteriosElegibilidad();
        ExperienciaEspecifica experiencia = criterios == null ? null : criterios.experienciaEspecifica();
        if (experiencia != null) {
            phrases.addAll(experiencia.safeObjetos());
        }
        PatronesDetectados patrones = profile.aprendizajeDePropuestas() == null
                ? null
                : profile.aprendizajeDePropuestas().patronesDetectados();
        if (patrones != null) {
            phrases.addAll(patrones.safePerfilNegocio());
        }
        return List.copyOf(phrases);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
