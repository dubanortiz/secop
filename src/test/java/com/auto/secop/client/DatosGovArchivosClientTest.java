package com.auto.secop.client;

import com.auto.secop.config.SecopProperties;
import com.auto.secop.model.SecopArchivo;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DatosGovArchivosClientTest {

    @Test
    void listsAttachmentsForPortfolio() {
        String baseUrl = "https://www.datos.gov.co/resource/dmgg-8hin.json";
        RestClient.Builder builder = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecopProperties properties = new SecopProperties(
                "https://www.datos.gov.co/resource/p6dx-8zbt.json",
                "classpath:parametria/parametria-ofertas.json",
                80,
                200,
                20,
                List.of(),
                "src/main/resources/estudios-previos",
                baseUrl
        );
        DatosGovArchivosClient client = new DatosGovArchivosClient(builder.build(), properties);
        URI expected = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("$select", DatosGovArchivosClient.SELECT_FIELDS)
                .queryParam("$where", "proceso='CO1.BDOS.10836230'")
                .queryParam("$limit", 100)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        server.expect(requestTo(expected))
                .andRespond(withSuccess("""
                        [{
                          "id_documento": "857202343",
                          "proceso": "CO1.BDOS.10836230",
                          "nombre_archivo": "ESTUDIO PREVIO MOLECULARES 2026.pdf",
                          "tamanno_archivo": "10",
                          "extensi_n": "pdf",
                          "url_descarga_documento": {
                            "url": "https://community.secop.gov.co/Public/Archive/RetrieveFile/Index?DocumentId=857202343"
                          }
                        }]
                        """, MediaType.APPLICATION_JSON));

        List<SecopArchivo> files = client.findByPortfolio("CO1.BDOS.10836230");

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().nombreArchivo()).isEqualTo("ESTUDIO PREVIO MOLECULARES 2026.pdf");
        assertThat(files.getFirst().downloadUrl()).contains("DocumentId=857202343");
        server.verify();
    }
}
