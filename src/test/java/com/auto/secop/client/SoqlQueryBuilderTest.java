package com.auto.secop.client;

import com.auto.secop.model.parametria.FiltrosBusqueda;
import com.auto.secop.model.parametria.ParametriaProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SoqlQueryBuilderTest {

    @Test
    void buildsOpenPublishedFiltersFromParametria() {
        ParametriaProfile profile = new ParametriaProfile(
                "2.0.1",
                "SECOP II",
                null,
                new FiltrosBusqueda(
                        List.of("Mínima cuantía", "Contratación régimen especial (con ofertas)"),
                        List.of("Publicado", "Abierto"),
                        List.of(),
                        List.of("Municipio de Curillo"),
                        List.of(),
                        List.of("Caquetá"),
                        List.of(),
                        30_000_000L,
                        200_000_000L,
                        2,
                        List.of(),
                        List.of()
                ),
                null,
                List.of(),
                null
        );

        String where = SoqlQueryBuilder.buildWhere(profile, null, List.of("Policía", "ICBF"));

        assertThat(where).contains("estado_de_apertura_del_proceso='Abierto'");
        assertThat(where).contains("estado_del_procedimiento IN ('Publicado','Abierto')");
        assertThat(where).contains("modalidad_de_contratacion IN ('Mínima cuantía','Contratación régimen especial (con ofertas)')");
        assertThat(where).contains("departamento_entidad='Caquetá'");
        assertThat(where).contains("upper(entidad) like '%MUNICIPIO DE CURILLO%'");
        assertThat(where).contains("upper(entidad) like '%POLICÍA%'").contains("upper(entidad) like '%POLICIA%'");
        assertThat(where).contains("upper(entidad) like '%ICBF%'");
    }

    @Test
    void departamentoOverrideReplacesProfileDepartment() {
        ParametriaProfile profile = new ParametriaProfile(
                "2.0.1",
                "SECOP II",
                null,
                new FiltrosBusqueda(
                        List.of("Mínima cuantía"),
                        List.of("Publicado"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("Caquetá"),
                        List.of(),
                        null,
                        null,
                        null,
                        List.of(),
                        List.of()
                ),
                null,
                List.of(),
                null
        );

        String where = SoqlQueryBuilder.buildWhere(profile, "Antioquia", List.of());
        assertThat(where).contains("departamento_entidad='Antioquia'");
        assertThat(where).doesNotContain("Caquetá");
    }

    @Test
    void escapesQuotesInSoqlLiterals() {
        assertThat(SoqlQueryBuilder.escape("O'Higgins")).isEqualTo("O''Higgins");
    }
}
