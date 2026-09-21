package com.auto.secop.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SecopDocumentClientTest {

    @Test
    void downloadsPdfBytes() {
        RestClient.Builder builder = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecopDocumentClient client = new SecopDocumentClient(builder.build());
        byte[] pdf = "%PDF-1.4 mock".getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo("https://community.secop.gov.co/file/1"))
                .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF)
                        .header("Content-Disposition", "attachment; filename=\"ESTUDIO PREVIO.pdf\""));

        SecopDocumentClient.FetchedDocument fetched = client.download("https://community.secop.gov.co/file/1");

        assertThat(fetched.blocked()).isFalse();
        assertThat(fetched.error()).isFalse();
        assertThat(fetched.content()).isEqualTo(pdf);
        assertThat(fetched.fileName()).isEqualTo("ESTUDIO PREVIO.pdf");
        server.verify();
    }

    @Test
    void detectsRecaptchaHtmlAsBlocked() {
        RestClient.Builder builder = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecopDocumentClient client = new SecopDocumentClient(builder.build());
        String html = "<!DOCTYPE html><html><title>ReCaptcha</title><div class=\"g-recaptcha\"></div></html>";
        server.expect(requestTo("https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=x"))
                .andRespond(withSuccess(html, MediaType.TEXT_HTML));

        SecopDocumentClient.FetchedDocument fetched = client.download(
                "https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=x");

        assertThat(fetched.blocked()).isTrue();
        assertThat(fetched.content()).isNull();
        assertThat(fetched.detail()).containsIgnoringCase("captcha");
        server.verify();
    }

    @Test
    void isBlockedRecognizesRedirectToRecaptcha() {
        assertThat(SecopDocumentClient.isBlocked(
                302,
                "/Public/Common/GoogleReCaptcha/Index?previousUrl=x",
                MediaType.TEXT_HTML,
                new byte[0]
        )).isTrue();
        assertThat(SecopDocumentClient.isBlocked(
                200,
                null,
                MediaType.APPLICATION_PDF,
                "%PDF-1.4".getBytes(StandardCharsets.UTF_8)
        )).isFalse();
    }
}
