package com.auto.secop.service;

import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.SecopProcess;
import com.auto.secop.model.parametria.AprendizajeDePropuestas;
import com.auto.secop.model.parametria.CriteriosElegibilidad;
import com.auto.secop.model.parametria.ExperienciaEspecifica;
import com.auto.secop.model.parametria.FiltrosBusqueda;
import com.auto.secop.model.parametria.ParametriaProfile;
import com.auto.secop.model.parametria.PatronesDetectados;
import com.auto.secop.model.parametria.Proveedor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessMatchScorerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneId.of("America/Bogota"));

    private ProcessMatchScorer scorer;
    private ParametriaProfile profile;

    @BeforeEach
    void setUp() {
        SecopProperties properties = new SecopProperties(
                "https://www.datos.gov.co/resource/p6dx-8zbt.json",
                "classpath:parametria/parametria-ofertas.json",
                80,
                200,
                20,
                List.of("Policía", "Ejército", "ICBF", "Armada", "Fuerza Aeroespacial", "Ministerio de Defensa", "INPEC", "SENA"),
                "src/main/resources/estudios-previos",
                "https://www.datos.gov.co/resource/dmgg-8hin.json"
        );
        scorer = new ProcessMatchScorer(CLOCK, properties);
        profile = sampleProfile();
    }

    @Test
    void highFitCurilloProcessReachesEighty() {
        SecopProcess process = process(
                "MUNICIPIO DE CURILLO",
                "Caquetá",
                "Curillo",
                "Suministro y confección de dotación y uniformes para funcionarios municipales",
                "Mínima cuantía",
                "33000000",
                "2026-09-25T00:00:00.000",
                "V1.53101500"
        );

        int score = scorer.score(process, profile, null);
        assertThat(score).isGreaterThanOrEqualTo(80);
        assertThat(scorer.scoreObject(process, profile)).isEqualTo(35);
        assertThat(scorer.scoreTerritory(process, profile, null)).isEqualTo(25);
        assertThat(scorer.scoreModality(process, profile)).isEqualTo(15);
        assertThat(scorer.scoreBudget(process, profile)).isEqualTo(15);
        assertThat(scorer.scoreTime(process, profile)).isEqualTo(10);
    }

    @Test
    void unspscAloneDoesNotPushUnrelatedObjectToEighty() {
        SecopProcess process = process(
                "DEPARTAMENTO DE POLICIA CAQUETA",
                "Caquetá",
                "Florencia",
                "Mantenimiento de motocicletas",
                "Mínima cuantía",
                "167900000",
                "2026-09-22T00:00:00.000",
                "V1.53101500"
        );

        assertThat(scorer.scoreObject(process, profile)).isEqualTo(8);
        assertThat(scorer.score(process, profile, null)).isLessThan(80);
    }

    @Test
    void motorcycleMaintenanceInCaquetaStaysBelowEighty() {
        SecopProcess process = process(
                "DEPARTAMENTO DE POLICIA CAQUETA - POLICIA NACIONAL DE COLOMBIA",
                "Caquetá",
                "Florencia",
                "Mantenimiento de motocicletas",
                "Mínima cuantía",
                "167900000",
                "2026-09-22T00:00:00.000",
                "V1.78181507"
        );

        assertThat(scorer.score(process, profile, null)).isLessThan(80);
        assertThat(scorer.scoreObject(process, profile)).isZero();
    }

    @Test
    void budgetFarAboveBandScoresZeroEvenIfObjectFits() {
        SecopProcess process = process(
                "MUNICIPIO DE FLORENCIA",
                "Caquetá",
                "Florencia",
                "Aunar esfuerzos técnicos, logísticos, administrativos y financieros",
                "Contratación régimen especial (con ofertas)",
                "385043450",
                "2026-09-23T00:00:00.000",
                "UNSPECIFIED"
        );

        assertThat(scorer.scoreBudget(process, profile)).isZero();
        assertThat(scorer.score(process, profile, null)).isLessThan(80);
    }

    @Test
    void logisticInflectionMatchesObjectKeyword() {
        SecopProcess process = process(
                "MUNICIPIO DE FLORENCIA",
                "Caquetá",
                "Florencia",
                "Aunar esfuerzos técnicos y logísticos para el festival de danzas",
                "Contratación régimen especial (con ofertas)",
                "64000000",
                "2026-09-25T00:00:00.000",
                null
        );

        assertThat(scorer.scoreObject(process, profile)).isEqualTo(35);
        assertThat(scorer.score(process, profile, null)).isGreaterThanOrEqualTo(80);
    }

    @Test
    void closerClosingDateScoresLowerButIsNotDiscarded() {
        SecopProcess process = process(
                "MUNICIPIO DE CURILLO",
                "Caquetá",
                "Curillo",
                "Apoyo logístico para festival de danzas",
                "Mínima cuantía",
                "49000000",
                "2026-09-21T00:00:00.000",
                null
        );

        assertThat(scorer.scoreTime(process, profile)).isEqualTo(5);
        assertThat(scorer.score(process, profile, null)).isGreaterThanOrEqualTo(80);
    }

    @Test
    void missingBudgetAndDateAreConservative() {
        SecopProcess process = process(
                "MUNICIPIO DE CURILLO",
                "Caquetá",
                "Curillo",
                "Suministro de uniformes",
                "Mínima cuantía",
                null,
                null,
                null
        );

        assertThat(scorer.scoreBudget(process, profile)).isEqualTo(4);
        assertThat(scorer.scoreTime(process, profile)).isEqualTo(3);
        assertThat(scorer.score(process, profile, null)).isLessThan(80);
    }

    @Test
    void unknownModalityScoresZero() {
        SecopProcess process = process(
                "MUNICIPIO DE CURILLO",
                "Caquetá",
                "Curillo",
                "Suministro de uniformes",
                "Contratación directa",
                "33000000",
                "2026-09-25T00:00:00.000",
                null
        );

        assertThat(scorer.scoreModality(process, profile)).isZero();
    }

    @Test
    void nationalEntityOutsideCaquetaNeedsStrongObjectFit() {
        SecopProcess process = process(
                "POLICÍA NACIONAL DE COLOMBIA",
                "Distrito Capital de Bogotá",
                "Bogotá",
                "Suministro y confección de dotación institucional",
                "Mínima cuantía",
                "80000000",
                "2026-09-30T00:00:00.000",
                "V1.53101500"
        );

        int score = scorer.score(process, profile, null);
        assertThat(scorer.scoreTerritory(process, profile, null)).isEqualTo(15);
        assertThat(score).isGreaterThanOrEqualTo(80);
    }

    private static SecopProcess process(
            String entity,
            String department,
            String city,
            String object,
            String modality,
            String budget,
            String closing,
            String unspsc
    ) {
        return new SecopProcess(
                "CO1.REQ.1",
                "REF-1",
                entity,
                department,
                city,
                "Territorial",
                object,
                object,
                "Presentación de oferta",
                budget,
                modality,
                closing,
                "Publicado",
                "Abierto",
                unspsc,
                "No definido",
                new SecopProcess.UrlProceso("https://community.secop.gov.co/example")
        );
    }

    private static ParametriaProfile sampleProfile() {
        return new ParametriaProfile(
                "2.0.1",
                "SECOP II",
                new Proveedor("SACHE FUNDACION", "ESAL", "Caquetá", "Municipio de Curillo"),
                new FiltrosBusqueda(
                        List.of("Mínima cuantía", "Contratación régimen especial (con ofertas)"),
                        List.of("Publicado", "Abierto"),
                        List.of("53101500", "90101603"),
                        List.of("Municipio de Curillo"),
                        List.of(),
                        List.of("Caquetá"),
                        List.of("Curillo", "Florencia"),
                        30_000_000L,
                        200_000_000L,
                        2,
                        List.of(
                                "dotación",
                                "uniformes",
                                "confección",
                                "suministro",
                                "logística",
                                "festival",
                                "danzas",
                                "aunar esfuerzos",
                                "apoyo logístico"
                        ),
                        List.of()
                ),
                new CriteriosElegibilidad(new ExperienciaEspecifica(List.of(
                        "suministro y confección de dotación",
                        "servicios logísticos para eventos"
                ))),
                List.of(),
                new AprendizajeDePropuestas(new PatronesDetectados(List.of(
                        "servicios logísticos para eventos municipales"
                )))
        );
    }
}
