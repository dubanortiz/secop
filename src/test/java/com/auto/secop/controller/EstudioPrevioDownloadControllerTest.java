package com.auto.secop.controller;

import com.auto.secop.model.EstudioPrevioDownloadResult;
import com.auto.secop.model.EstudioPrevioDownloadStatus;
import com.auto.secop.model.ProcessMatchResponse;
import com.auto.secop.service.EstudioPrevioDownloadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {EstudioPrevioDownloadController.class, ApiExceptionHandler.class})
class EstudioPrevioDownloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EstudioPrevioDownloadService service;

    @Test
    void acceptsSearchResultArray() throws Exception {
        given(service.download(anyList())).willReturn(List.of(
                EstudioPrevioDownloadResult.of(
                        "MC-055-DISAN-EJC-2026",
                        EstudioPrevioDownloadStatus.DOWNLOADED,
                        List.of(new EstudioPrevioDownloadResult.SavedFile(
                                "ESTUDIO PREVIO.pdf",
                                "src/main/resources/estudios-previos/MC-055-DISAN-EJC-2026/ESTUDIO PREVIO.pdf",
                                1234
                        )),
                        null
                )
        ));

        mockMvc.perform(post("/api/v1/processes/estudios-previos/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{
                                  "processNumber": "MC-055-DISAN-EJC-2026",
                                  "matchPercent": 90,
                                  "entity": "DIRECCIÓN DE SANIDAD EJÉRCITO1",
                                  "object": "SUMINISTRO",
                                  "modality": "Mínima cuantía",
                                  "budget": 52000010,
                                  "closingDate": "2026-09-22",
                                  "url": "https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.10893271"
                                }]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].processNumber").value("MC-055-DISAN-EJC-2026"))
                .andExpect(jsonPath("$[0].status").value("DOWNLOADED"))
                .andExpect(jsonPath("$[0].files[0].fileName").value("ESTUDIO PREVIO.pdf"))
                .andExpect(jsonPath("$[0].files[0].sizeBytes").value(1234));

        verify(service).download(org.mockito.ArgumentMatchers.<List<ProcessMatchResponse>>argThat(list ->
                list.size() == 1 && "MC-055-DISAN-EJC-2026".equals(list.getFirst().processNumber())));
    }

    @Test
    void acceptsProcessesWrapperAndMapsMissingDocument() throws Exception {
        given(service.download(anyList())).willReturn(List.of(
                EstudioPrevioDownloadResult.of("X-1", EstudioPrevioDownloadStatus.NOT_FOUND, List.of(), "No Estudio Previo")
        ));

        mockMvc.perform(post("/api/v1/processes/estudios-previos/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"processes":[{"processNumber":"X-1","matchPercent":80,"entity":"E","object":"O",
                                "modality":"Mínima cuantía","budget":1,"closingDate":"2026-09-22","url":"https://example.test"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("NOT_FOUND"))
                .andExpect(jsonPath("$[0].error").value("No Estudio Previo"));
    }

    @Test
    void mapsBlockedDownload() throws Exception {
        given(service.download(anyList())).willReturn(List.of(
                EstudioPrevioDownloadResult.of("X-1", EstudioPrevioDownloadStatus.BLOCKED, List.of(), "captcha required")
        ));

        mockMvc.perform(post("/api/v1/processes/estudios-previos/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"processNumber\":\"X-1\",\"matchPercent\":1,\"url\":\"https://community.secop.gov.co/x\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$[0].error").value("captcha required"));
    }

    @Test
    void rejectsNonArrayNonWrapperBody() throws Exception {
        mockMvc.perform(post("/api/v1/processes/estudios-previos/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foo\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Body must be a JSON array of ProcessMatchResponse or {\"processes\": [...]}"));
    }
}
