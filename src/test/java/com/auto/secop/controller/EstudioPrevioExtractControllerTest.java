package com.auto.secop.controller;

import com.auto.secop.model.EstudioPrevioExtractMethod;
import com.auto.secop.model.EstudioPrevioExtractResult;
import com.auto.secop.model.EstudioPrevioExtractStatus;
import com.auto.secop.model.ProcessMatchResponse;
import com.auto.secop.service.EstudioPrevioExtractService;
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

@WebMvcTest(controllers = {EstudioPrevioExtractController.class, ApiExceptionHandler.class})
class EstudioPrevioExtractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EstudioPrevioExtractService service;

    @Test
    void acceptsSearchResultArray() throws Exception {
        given(service.extract(anyList())).willReturn(List.of(
                EstudioPrevioExtractResult.of(
                        "MC-055-DISAN-EJC-2026",
                        EstudioPrevioExtractStatus.EXTRACTED,
                        List.of(new EstudioPrevioExtractResult.ExtractedFile(
                                "src/main/resources/estudios-previos/MC-055-DISAN-EJC-2026/ESTUDIO PREVIO.pdf",
                                "src/main/resources/estudios-previos/MC-055-DISAN-EJC-2026/ESTUDIO PREVIO.txt",
                                EstudioPrevioExtractMethod.TEXT,
                                128
                        )),
                        null
                )
        ));

        mockMvc.perform(post("/api/v1/processes/estudios-previos/extract")
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
                .andExpect(jsonPath("$[0].status").value("EXTRACTED"))
                .andExpect(jsonPath("$[0].files[0].pdf").value(
                        "src/main/resources/estudios-previos/MC-055-DISAN-EJC-2026/ESTUDIO PREVIO.pdf"))
                .andExpect(jsonPath("$[0].files[0].textFile").value(
                        "src/main/resources/estudios-previos/MC-055-DISAN-EJC-2026/ESTUDIO PREVIO.txt"))
                .andExpect(jsonPath("$[0].files[0].method").value("text"))
                .andExpect(jsonPath("$[0].files[0].chars").value(128));

        verify(service).extract(org.mockito.ArgumentMatchers.<List<ProcessMatchResponse>>argThat(list ->
                list.size() == 1 && "MC-055-DISAN-EJC-2026".equals(list.getFirst().processNumber())));
    }

    @Test
    void acceptsProcessNumberArrayAndMapsMissingPdf() throws Exception {
        given(service.extract(anyList())).willReturn(List.of(
                EstudioPrevioExtractResult.of(
                        "X-1",
                        EstudioPrevioExtractStatus.MISSING_PDF,
                        List.of(),
                        "No Estudio Previo PDF on disk"
                )
        ));

        mockMvc.perform(post("/api/v1/processes/estudios-previos/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"X-1\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("MISSING_PDF"))
                .andExpect(jsonPath("$[0].error").value("No Estudio Previo PDF on disk"));

        verify(service).extract(org.mockito.ArgumentMatchers.<List<ProcessMatchResponse>>argThat(list ->
                list.size() == 1 && "X-1".equals(list.getFirst().processNumber())));
    }

    @Test
    void mapsOcrMethod() throws Exception {
        given(service.extract(anyList())).willReturn(List.of(
                EstudioPrevioExtractResult.of(
                        "X-1",
                        EstudioPrevioExtractStatus.EXTRACTED,
                        List.of(new EstudioPrevioExtractResult.ExtractedFile(
                                "a.pdf", "a.txt", EstudioPrevioExtractMethod.OCR, 40
                        )),
                        null
                )
        ));

        mockMvc.perform(post("/api/v1/processes/estudios-previos/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processNumbers\":[\"X-1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].files[0].method").value("ocr"));
    }

    @Test
    void rejectsUnknownObjectShape() throws Exception {
        mockMvc.perform(post("/api/v1/processes/estudios-previos/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foo\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Body must be a JSON array of ProcessMatchResponse or {\"processes\": [...]}"));
    }
}
