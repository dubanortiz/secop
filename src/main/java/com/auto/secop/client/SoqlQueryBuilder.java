package com.auto.secop.client;

import com.auto.secop.model.parametria.FiltrosBusqueda;
import com.auto.secop.model.parametria.ParametriaProfile;
import com.auto.secop.support.TextNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds a SoQL {@code $where} clause for the public datos.gov.co SECOP II dataset (p6dx-8zbt).
 *
 * <p>When {@code departamento} is set, territory is <strong>only</strong> that department
 * (accent variants). National-entity keywords are not OR-ed, so SENA Antioquia cannot
 * leak into a Caquetá search. In-department national bodies (Policía Caquetá, ICBF Regional
 * Caquetá) still match because {@code departamento_entidad} is Caquetá.
 *
 * <p>When {@code departamento} is omitted, territory is profile departments (Caquetá)
 * <strong>or</strong> {@code entidades_interes} <strong>or</strong> national-entity stems.
 *
 * <p>LIKE stems are ASCII-folded. SODA {@code upper()} does not strip accents, so
 * {@code POLIC} matches both {@code POLICIA} and {@code POLICÍA} without putting
 * combining marks in the query string.
 */
public final class SoqlQueryBuilder {

    public static final String SELECT_FIELDS = String.join(",",
            "id_del_proceso",
            "referencia_del_proceso",
            "entidad",
            "departamento_entidad",
            "ciudad_entidad",
            "ordenentidad",
            "nombre_del_procedimiento",
            "descripci_n_del_procedimiento",
            "fase",
            "precio_base",
            "modalidad_de_contratacion",
            "fecha_de_recepcion_de",
            "estado_del_procedimiento",
            "estado_de_apertura_del_proceso",
            "codigo_principal_de_categoria",
            "categorias_adicionales",
            "urlproceso"
    );

    private SoqlQueryBuilder() {
    }

    public static String buildWhere(
            ParametriaProfile profile,
            String departamentoOverride,
            List<String> nationalEntities
    ) {
        FiltrosBusqueda filtros = profile.filtros();
        List<String> clauses = new ArrayList<>();
        clauses.add("estado_de_apertura_del_proceso='Abierto'");

        List<String> procedureStates = filtros.safeEstadosProceso().stream()
                .map(SoqlQueryBuilder::escape)
                .toList();
        if (!procedureStates.isEmpty()) {
            clauses.add(inClause("estado_del_procedimiento", procedureStates));
        }

        List<String> modalities = filtros.safeModalidades().stream()
                .map(SoqlQueryBuilder::escape)
                .toList();
        if (!modalities.isEmpty()) {
            clauses.add(inClause("modalidad_de_contratacion", modalities));
        }

        boolean restrictToDepartment = departamentoOverride != null && !departamentoOverride.isBlank();
        List<String> territory = new ArrayList<>(departmentClauses(
                restrictToDepartment ? List.of(departamentoOverride.trim()) : filtros.safeDepartamentos()
        ));

        if (!restrictToDepartment) {
            for (String entity : filtros.safeEntidadesInteres()) {
                String folded = asciiUpperStem(entity);
                if (!folded.isBlank()) {
                    territory.add("upper(entidad) like '%" + escape(folded) + "%'");
                }
            }
            for (String stem : nationalLikeStems(nationalEntities)) {
                territory.add("upper(entidad) like '%" + escape(stem) + "%'");
            }
        }
        if (!territory.isEmpty()) {
            clauses.add("(" + String.join(" OR ", territory) + ")");
        }
        return String.join(" AND ", clauses);
    }

    static List<String> departmentClauses(List<String> departments) {
        Set<String> clauses = new LinkedHashSet<>();
        for (String department : departments) {
            if (department == null || department.isBlank()) {
                continue;
            }
            for (String variant : departmentVariants(department)) {
                clauses.add("departamento_entidad='" + escape(variant) + "'");
                clauses.add("upper(departamento_entidad)='" + escape(variant.toUpperCase(Locale.ROOT)) + "'");
            }
        }
        return List.copyOf(clauses);
    }

    /**
     * Live dataset stores Caquetá as {@code Caquetá} only; also accept {@code Caqueta}/{@code CAQUETA}.
     * No DIVIPOLA column exists on p6dx-8zbt.
     */
    static List<String> departmentVariants(String department) {
        Set<String> variants = new LinkedHashSet<>();
        String trimmed = department.trim();
        variants.add(trimmed);
        String folded = TextNormalizer.stripAccents(trimmed);
        if (!folded.isBlank()) {
            variants.add(folded);
        }
        return List.copyOf(variants);
    }

    /**
     * ASCII-only LIKE stems. Accented source names (Policía, Ejército) are folded so the
     * query string never contains mojibake or combining marks.
     */
    static List<String> nationalLikeStems(List<String> nationalEntities) {
        if (nationalEntities == null) {
            return List.of();
        }
        Set<String> stems = new LinkedHashSet<>();
        for (String entity : nationalEntities) {
            if (entity == null || entity.isBlank()) {
                continue;
            }
            String folded = asciiUpperStem(entity);
            if (folded.isBlank()) {
                continue;
            }
            stems.add(folded);
            if (folded.startsWith("POLIC")) {
                stems.add("POLIC");
            } else if (folded.startsWith("EJERCIT")) {
                stems.add("EJERCIT");
            } else if (folded.startsWith("FUERZA AER")) {
                stems.add("FUERZA AER");
            }
        }
        return List.copyOf(stems);
    }

    private static String inClause(String field, List<String> values) {
        List<String> quoted = values.stream()
                .map(value -> "'" + value + "'")
                .toList();
        return field + " IN (" + String.join(",", quoted) + ")";
    }

    static String asciiUpperStem(String raw) {
        String folded = TextNormalizer.stripAccents(raw).toUpperCase(Locale.ROOT);
        return folded.replaceAll("[^A-Z0-9 ]+", " ").replaceAll("\\s+", " ").trim();
    }

    static String escape(String value) {
        return value.replace("'", "''");
    }
}
