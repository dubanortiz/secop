package com.auto.secop.client;

import com.auto.secop.model.parametria.FiltrosBusqueda;
import com.auto.secop.model.parametria.ParametriaProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SoqlQueryBuilderTest {

    private static final List<String> NATIONAL = List.of(
            "Policía", "Ejército", "ICBF", "Armada", "Fuerza Aeroespacial", "Fuerza Aérea",
            "Ministerio de Defensa", "INPEC", "SENA"
    );

    @Test
    void defaultSearchOrsCaquetaAndNationalKeywords() {
        String where = SoqlQueryBuilder.buildWhere(profile(), null, NATIONAL);

        assertThat(where).contains("estado_de_apertura_del_proceso='Abierto'");
        assertThat(where).contains("estado_del_procedimiento IN ('Publicado','Abierto')");
        assertThat(where).contains("modalidad_de_contratacion IN ('Mínima cuantía','Contratación régimen especial (con ofertas)')");
        assertThat(where).contains("departamento_entidad='Caquetá'");
        assertThat(where).contains("departamento_entidad='Caqueta'");
        assertThat(where).contains("upper(departamento_entidad)='CAQUETA'");
        assertThat(where).contains("upper(entidad) like '%MUNICIPIO DE CURILLO%'");
        assertThat(where).contains("upper(entidad) like '%POLICIA%'");
        assertThat(where).contains("upper(entidad) like '%POLIC%'");
        assertThat(where).contains("upper(entidad) like '%SENA%'");
        assertThat(where).contains("upper(entidad) like '%ICBF%'");
        assertThat(where).doesNotContain("Ã");
        assertThat(where).doesNotContain("POLICÍA").doesNotContain("EJÉRCITO");
    }

    @Test
    void departamentoRestrictsToThatDepartmentAndDropsNationalOr() {
        String where = SoqlQueryBuilder.buildWhere(profile(), "Caquetá", NATIONAL);

        assertThat(where).contains("departamento_entidad='Caquetá'");
        assertThat(where).contains("departamento_entidad='Caqueta'");
        assertThat(where).contains("upper(departamento_entidad)='CAQUETA'");
        assertThat(where).doesNotContain("SENA");
        assertThat(where).doesNotContain("POLIC");
        assertThat(where).doesNotContain("ICBF");
        assertThat(where).doesNotContain("MUNICIPIO DE CURILLO");
        assertThat(where).doesNotContain("Antioquia");
    }

    @Test
    void departamentoOverrideReplacesProfileDepartment() {
        String where = SoqlQueryBuilder.buildWhere(profile(), "Antioquia", NATIONAL);
        assertThat(where).contains("departamento_entidad='Antioquia'");
        assertThat(where).doesNotContain("Caquetá");
        assertThat(where).doesNotContain("SENA");
    }

    @Test
    void likePatternsInDefaultSearchAreAscii() {
        String where = SoqlQueryBuilder.buildWhere(profile(), null, NATIONAL);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("like '%([^']+)%'").matcher(where);
        while (matcher.find()) {
            String stem = matcher.group(1);
            assertThat(stem.chars().allMatch(c -> c < 128))
                    .as("LIKE stem should be ASCII: %s", stem)
                    .isTrue();
            assertThat(stem).doesNotContain("Ã");
        }
    }

    @Test
    void nationalLikeStemsAreAsciiFolded() {
        List<String> stems = SoqlQueryBuilder.nationalLikeStems(List.of("Policía", "Ejército", "Fuerza Aérea"));
        assertThat(stems).contains("POLICIA", "POLIC", "EJERCITO", "EJERCIT", "FUERZA AEREA", "FUERZA AER");
        assertThat(stems).noneMatch(stem -> stem.contains("Ã") || stem.indexOf('\u0301') >= 0);
        assertThat(stems).noneMatch(stem -> stem.chars().anyMatch(c -> c > 127));
    }

    @Test
    void departmentVariantsIncludeAccentAndAscii() {
        assertThat(SoqlQueryBuilder.departmentVariants("Caquetá"))
                .containsExactly("Caquetá", "Caqueta");
    }

    @Test
    void escapesQuotesInSoqlLiterals() {
        assertThat(SoqlQueryBuilder.escape("O'Higgins")).isEqualTo("O''Higgins");
    }

    private static ParametriaProfile profile() {
        return new ParametriaProfile(
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
                        List.of("Curillo", "Florencia"),
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
    }
}
