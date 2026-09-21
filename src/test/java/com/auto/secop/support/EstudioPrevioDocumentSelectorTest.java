package com.auto.secop.support;

import com.auto.secop.model.SecopArchivo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EstudioPrevioDocumentSelectorTest {

    @Test
    void prefersEstudioPrevioPdfOverInvitacionZip() {
        SecopArchivo pdf = archivo("ESTUDIO PREVIO MOLECULARES 2026.pdf", "pdf");
        SecopArchivo zip = archivo("INVITACION PUBLICA.zip", "zip");
        SecopArchivo cdp = archivo("CDP.pdf", "pdf");

        assertThat(EstudioPrevioDocumentSelector.select(List.of(cdp, zip, pdf)))
                .containsExactly(pdf);
    }

    @Test
    void fallsBackToInvitacionPackageWhenNoPdfMatches() {
        SecopArchivo zip = archivo("INVITACION PUBLICA MC-055.zip", "zip");
        SecopArchivo cdp = archivo("CDP.pdf", "pdf");

        assertThat(EstudioPrevioDocumentSelector.select(List.of(cdp, zip)))
                .containsExactly(zip);
    }

    @Test
    void matchesAccentAndUnderscoreVariants() {
        assertThat(EstudioPrevioDocumentSelector.isEstudioPrevioPdf(
                archivo("Estudios_Previos.PDF", "PDF"))).isTrue();
        assertThat(EstudioPrevioDocumentSelector.isEstudioPrevioPdf(
                archivo("2-ESTUDIOS PREVIOS.pdf", "pdf"))).isTrue();
        assertThat(EstudioPrevioDocumentSelector.isEstudioPrevioPdf(
                archivo("INVITACION PUBLICA.pdf", "pdf"))).isFalse();
    }

    private static SecopArchivo archivo(String name, String extension) {
        return new SecopArchivo("1", "CO1.BDOS.1", name, "10", extension, name, new SecopArchivo.Url("https://example.test/" + name));
    }
}
