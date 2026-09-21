package com.auto.secop.support;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SafePathsTest {

    @Test
    void sanitizesProcessFolderFromProcessNumber() {
        assertThat(SafePaths.processFolder("MC-2026-047")).isEqualTo("MC-2026-047");
        assertThat(SafePaths.processFolder("  129-CMC-2026  ")).isEqualTo("129-CMC-2026");
        assertThat(SafePaths.processFolder("MC/055 DISAN")).isEqualTo("MC-055-DISAN");
        assertThat(SafePaths.processFolder("../etc/passwd")).isEqualTo("etc-passwd");
        assertThat(SafePaths.processFolder("")).isEqualTo("sin-numero");
    }

    @Test
    void textSidecarReplacesPdfExtension() {
        Path pdf = Path.of("src/main/resources/estudios-previos/MC-2026-047/ESTUDIO PREVIO.pdf");
        assertThat(SafePaths.textSidecar(pdf))
                .isEqualTo(Path.of("src/main/resources/estudios-previos/MC-2026-047/ESTUDIO PREVIO.txt"));
        assertThat(SafePaths.looksLikePdf(pdf)).isTrue();
    }

    @Test
    void extractsNoticeUidAndCanonicalUrl() {
        String url = "https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.10893271&isFromPublicArea=True";
        assertThat(SafePaths.noticeUid(url)).contains("CO1.NTC.10893271");
        assertThat(SafePaths.canonicalOpportunityUrl(url))
                .isEqualTo("https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.10893271");
    }
}
