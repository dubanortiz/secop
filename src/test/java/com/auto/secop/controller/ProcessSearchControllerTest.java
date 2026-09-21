package com.auto.secop.controller;

import com.auto.secop.client.DatosGovSecopClient;
import com.auto.secop.client.DatosGovUnavailableException;
import com.auto.secop.config.ParametriaConfiguration;
import com.auto.secop.config.SecopApplicationConfiguration;
import com.auto.secop.model.SecopProcess;
import com.auto.secop.service.ProcessMatchScorer;
import com.auto.secop.service.ProcessSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProcessSearchController.class, HealthController.class, ApiExceptionHandler.class})
@Import({
        ProcessSearchService.class,
        ProcessMatchScorer.class,
        ParametriaConfiguration.class,
        SecopApplicationConfiguration.class,
        ProcessSearchControllerTest.FixedClockConfig.class
})
class ProcessSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatosGovSecopClient client;

    @Test
    void pingStillWorks() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void searchReturnsOnlyMatchesAtOrAboveMinMatch() throws Exception {
        given(client.findOpenProcesses(any(), isNull())).willReturn(List.of(
                process(
                        "129-CMC-2026",
                        "MUNICIPIO DE CURILLO",
                        "Caquetá",
                        "Curillo",
                        "Suministro y confección de dotación y uniformes",
                        "Mínima cuantía",
                        "33000000",
                        "2026-09-25T00:00:00.000",
                        "https://community.secop.gov.co/high",
                        "V1.53101500"
                ),
                process(
                        "PN-MOTOS",
                        "DEPARTAMENTO DE POLICIA CAQUETA",
                        "Caquetá",
                        "Florencia",
                        "Mantenimiento de motocicletas",
                        "Mínima cuantía",
                        "167900000",
                        "2026-09-22T00:00:00.000",
                        "https://community.secop.gov.co/low",
                        "V1.78181507"
                )
        ));

        mockMvc.perform(get("/api/v1/processes/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].processNumber").value("129-CMC-2026"))
                .andExpect(jsonPath("$[0].matchPercent").value(org.hamcrest.Matchers.greaterThanOrEqualTo(80)))
                .andExpect(jsonPath("$[0].entity").value("MUNICIPIO DE CURILLO"))
                .andExpect(jsonPath("$[0].modality").value("Mínima cuantía"))
                .andExpect(jsonPath("$[0].budget").value(33000000))
                .andExpect(jsonPath("$[0].closingDate").value("2026-09-25"))
                .andExpect(jsonPath("$[0].url").value("https://community.secop.gov.co/high"));
    }

    @Test
    void searchWithDepartamentoDoesNotReturnOtherDepartments() throws Exception {
        given(client.findOpenProcesses(any(), eq("Caquetá"))).willReturn(List.of(
                process(
                        "129-CMC-2026",
                        "MUNICIPIO DE CURILLO",
                        "Caquetá",
                        "Curillo",
                        "Suministro y confección de dotación y uniformes",
                        "Mínima cuantía",
                        "33000000",
                        "2026-09-25T00:00:00.000",
                        "https://community.secop.gov.co/high",
                        "V1.53101500"
                ),
                process(
                        "MC-ANT-CFDCM-088-2026",
                        "SENA REGIONAL ANTIOQUIA",
                        "Antioquia",
                        "Itagüí",
                        "Suministro y confección de dotación institucional",
                        "Mínima cuantía",
                        "155398000",
                        "2026-09-22T00:00:00.000",
                        "https://community.secop.gov.co/sena",
                        "V1.25172500"
                )
        ));

        mockMvc.perform(get("/api/v1/processes/search")
                        .param("minMatch", "80")
                        .param("departamento", "Caquetá")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].processNumber").value("129-CMC-2026"))
                .andExpect(jsonPath("$[0].entity").value("MUNICIPIO DE CURILLO"));
    }

    @Test
    void searchWithoutDepartamentoCanReturnNationalSena() throws Exception {
        given(client.findOpenProcesses(any(), isNull())).willReturn(List.of(
                process(
                        "MC-ANT-CFDCM-088-2026",
                        "SENA REGIONAL ANTIOQUIA",
                        "Antioquia",
                        "Itagüí",
                        "Suministro y confección de dotación institucional",
                        "Mínima cuantía",
                        "155398000",
                        "2026-09-22T00:00:00.000",
                        "https://community.secop.gov.co/sena",
                        "V1.25172500"
                )
        ));

        mockMvc.perform(get("/api/v1/processes/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].processNumber").value("MC-ANT-CFDCM-088-2026"))
                .andExpect(jsonPath("$[0].matchPercent").value(org.hamcrest.Matchers.greaterThanOrEqualTo(80)));
    }

    @Test
    void searchRejectsInvalidMinMatch() throws Exception {
        mockMvc.perform(get("/api/v1/processes/search").param("minMatch", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("minMatch must be between 0 and 100"));
    }

    @Test
    void searchMapsDatosGovFailuresToBadGateway() throws Exception {
        given(client.findOpenProcesses(any(), any()))
                .willThrow(new DatosGovUnavailableException("timeout", new RuntimeException("boom")));

        mockMvc.perform(get("/api/v1/processes/search"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Unable to query datos.gov.co"));
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
            String url,
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
                new SecopProcess.UrlProceso(url)
        );
    }

    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        Clock americaBogotaClock() {
            return Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneId.of("America/Bogota"));
        }
    }
}
