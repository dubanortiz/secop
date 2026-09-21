package com.auto.secop.service;

import com.auto.secop.client.DatosGovSecopClient;
import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.ProcessMatchResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ProcessSearchServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneId.of("America/Bogota"));

    private DatosGovSecopClient client;
    private ProcessSearchService service;

    @BeforeEach
    void setUp() {
        client = mock(DatosGovSecopClient.class);
        SecopProperties properties = new SecopProperties(
                "https://www.datos.gov.co/resource/p6dx-8zbt.json",
                "classpath:parametria/parametria-ofertas.json",
                80,
                200,
                20,
                List.of("Policía", "SENA", "ICBF"),
                "src/main/resources/estudios-previos",
                "https://www.datos.gov.co/resource/dmgg-8hin.json"
        );
        ProcessMatchScorer scorer = new ProcessMatchScorer(CLOCK, properties);
        service = new ProcessSearchService(client, scorer, sampleProfile(), properties);
    }

    @Test
    void departamentoFilterDropsSenaFromOtherDepartments() {
        given(client.findOpenProcesses(any(), eq("Caquetá"))).willReturn(List.of(
                curilloDotacion(),
                senaAntioquiaSuministro()
        ));

        List<ProcessMatchResponse> matches = service.search(80, "Caquetá", 10);

        assertThat(matches).extracting(ProcessMatchResponse::processNumber)
                .containsExactly("129-CMC-2026")
                .doesNotContain("MC-ANT-CFDCM-088-2026");
        assertThat(matches).allMatch(match -> match.matchPercent() >= 80);
    }

    @Test
    void defaultSearchCanReturnNationalSenaWhenScoreIsHighEnough() {
        given(client.findOpenProcesses(any(), isNull())).willReturn(List.of(senaAntioquiaSuministro()));

        List<ProcessMatchResponse> matches = service.search(80, null, 10);

        assertThat(matches).extracting(ProcessMatchResponse::processNumber)
                .containsExactly("MC-ANT-CFDCM-088-2026");
        assertThat(matches.getFirst().matchPercent()).isGreaterThanOrEqualTo(80);
    }

    @Test
    void matchesRequestedDepartmentFoldsAccents() {
        assertThat(ProcessSearchService.matchesRequestedDepartment(curilloDotacion(), "Caqueta")).isTrue();
        assertThat(ProcessSearchService.matchesRequestedDepartment(curilloDotacion(), "CAQUETÁ")).isTrue();
        assertThat(ProcessSearchService.matchesRequestedDepartment(senaAntioquiaSuministro(), "Caquetá")).isFalse();
        assertThat(ProcessSearchService.matchesRequestedDepartment(senaAntioquiaSuministro(), null)).isTrue();
    }

    private static SecopProcess curilloDotacion() {
        return process(
                "129-CMC-2026",
                "MUNICIPIO DE CURILLO",
                "Caquetá",
                "Curillo",
                "Suministro y confección de dotación y uniformes",
                "Mínima cuantía",
                "33000000",
                "2026-09-25T00:00:00.000",
                "V1.53101500"
        );
    }

    private static SecopProcess senaAntioquiaSuministro() {
        return process(
                "MC-ANT-CFDCM-088-2026",
                "SENA REGIONAL ANTIOQUIA Grupo Administrativo Complejo Itagüí",
                "Antioquia",
                "Itagüí",
                "Suministro y confección de dotación institucional",
                "Mínima cuantía",
                "155398000",
                "2026-09-22T00:00:00.000",
                "V1.25172500"
        );
    }

    private static SecopProcess process(
            String number,
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
                "CO1.REQ." + number,
                number,
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
                new SecopProcess.UrlProceso("https://community.secop.gov.co/" + number)
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
                        List.of("53101500"),
                        List.of("Municipio de Curillo"),
                        List.of(),
                        List.of("Caquetá"),
                        List.of("Curillo", "Florencia"),
                        30_000_000L,
                        200_000_000L,
                        2,
                        List.of("dotación", "uniformes", "confección", "suministro"),
                        List.of()
                ),
                new CriteriosElegibilidad(new ExperienciaEspecifica(List.of())),
                List.of(),
                new AprendizajeDePropuestas(new PatronesDetectados(List.of()))
        );
    }
}
