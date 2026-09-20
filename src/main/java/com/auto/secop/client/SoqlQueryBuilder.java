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
 * Builds a SoQL $where clause for the public datos.gov.co SECOP II dataset (p6dx-8zbt).
 * Read-only filters from parametría {@code filtros_busqueda} plus national-entity intent.
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

        List<String> territory = new ArrayList<>();
        List<String> departments = new ArrayList<>();
        if (departamentoOverride != null && !departamentoOverride.isBlank()) {
            departments.add(departamentoOverride.trim());
        } else {
            departments.addAll(filtros.safeDepartamentos());
        }
        for (String department : departments) {
            territory.add("departamento_entidad='" + escape(department) + "'");
        }
        for (String entity : filtros.safeEntidadesInteres()) {
            territory.add("upper(entidad) like '%" + escape(entity).toUpperCase(Locale.ROOT) + "%'");
        }
        for (String stem : nationalLikeStems(nationalEntities)) {
            territory.add("upper(entidad) like '%" + escape(stem) + "%'");
        }
        if (!territory.isEmpty()) {
            clauses.add("(" + String.join(" OR ", territory) + ")");
        }
        return String.join(" AND ", clauses);
    }

    static List<String> nationalLikeStems(List<String> nationalEntities) {
        if (nationalEntities == null) {
            return List.of();
        }
        Set<String> stems = new LinkedHashSet<>();
        for (String entity : nationalEntities) {
            if (entity == null || entity.isBlank()) {
                continue;
            }
            String raw = entity.trim().toUpperCase(Locale.ROOT);
            stems.add(raw);
            String folded = TextNormalizer.normalize(entity).toUpperCase(Locale.ROOT);
            if (!folded.isBlank()) {
                stems.add(folded);
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

    static String escape(String value) {
        return value.replace("'", "''");
    }
}
