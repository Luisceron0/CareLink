package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.KnowledgeQuery;
import com.carelink.clinical.domain.KnowledgeResult;
import com.carelink.clinical.domain.port.KnowledgeEngineRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.persistence.PostgresIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * FR-CLN-06, FR-CLN-07, ADR-006, ADR-007.
 *
 * <p><b>Cómo se construye la consulta, y por qué así.</b> Los filtros son opcionales, o
 * sea que el SQL es dinámico — el mismo patrón que §8.4 marca como vector de inyección.
 * Acá NINGÚN valor de usuario se concatena: lo que se arma dinámicamente es solo la
 * presencia o ausencia de fragmentos {@code AND columna = ?} fijos, escritos en este
 * archivo, y todo valor viaja como parámetro posicional. Un filtro que el cliente no
 * mandó simplemente no agrega su fragmento. Lo único interpolado en el string es el
 * nombre del schema, que viene de configuración de despliegue (no de un request) y aun
 * así pasa por revalidación contra {@code TenantSlug.PATTERN} y comillado
 * ({@link PostgresIdentifiers#quote}) — mismo tratamiento que el resto de los
 * repositorios de este paquete (AC-05).
 *
 * <p><b>k-anonimato (FR-CLN-07, ADR-007).</b> El umbral se aplica en un {@code HAVING
 * COUNT(DISTINCT patient_id) >= ?} dentro de la consulta, no descartando filas en Java
 * después de traerlas. La diferencia importa: si el filtro viviera en memoria, las filas
 * por debajo del umbral igual habrían salido de la base y habrían pasado por logs,
 * heap y —si algo fallara a mitad de camino— potencialmente por una respuesta de error.
 * Con el {@code HAVING}, un grupo con menos de k pacientes distintos no existe para el
 * resto del sistema.
 *
 * <p>Se cuenta {@code COUNT(DISTINCT patient_id)}, no {@code COUNT(*)}: diez
 * intervenciones sobre el mismo paciente son un solo paciente re-identificable, y contar
 * intervenciones dejaría pasar exactamente el caso que ADR-007 quiere prevenir.
 */
@Repository
public class JdbcKnowledgeEngineRepository implements KnowledgeEngineRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeEngineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public KnowledgeResult search(TenantSlug tenantSlug, KnowledgeQuery query, int kAnonymityThreshold) {
        String schema = schemaOf(tenantSlug);

        StringBuilder sql = new StringBuilder()
                .append("SELECT i.nic_code, i.noc_code, ")
                .append("COUNT(*) AS intervention_count, ")
                .append("COUNT(DISTINCT i.patient_id) AS distinct_patients, ")
                .append("AVG(i.effectiveness) AS avg_effectiveness ")
                .append("FROM ").append(schema).append(".health_interventions i ")
                // JOIN a patients solo por los filtros demográficos. INNER y no LEFT: una
                // intervención sin paciente correspondiente sería datos corruptos, y
                // arrastrarla a un agregado que se lee como evidencia clínica es peor que
                // omitirla.
                .append("JOIN ").append(schema).append(".patients p ON p.id = i.patient_id ")
                // Solo intervenciones YA evaluadas: una sin outcome no aporta evidencia de
                // efectividad, que es lo único que esta consulta responde.
                .append("WHERE i.effectiveness IS NOT NULL ");

        List<Object> params = new ArrayList<>();

        if (notBlank(query.diagnosisCie10())) {
            sql.append("AND i.diagnosis_cie10 = ? ");
            params.add(query.diagnosisCie10());
        }
        if (notBlank(query.nandaCode())) {
            sql.append("AND i.nanda_code = ? ");
            params.add(query.nandaCode());
        }
        if (query.sex() != null) {
            sql.append("AND p.sex = ? ");
            params.add(query.sex().name());
        }
        // Edad: date_of_birth está cifrada y no admite comparación por rango en SQL.
        // Ver ageFilterUnsupported() para por qué esto lanza en vez de ignorarse.
        if (query.minAge() != null || query.maxAge() != null) {
            throw new UnsupportedOperationException(ageFilterUnsupported());
        }

        sql.append("GROUP BY i.nic_code, i.noc_code ")
                .append("HAVING COUNT(DISTINCT i.patient_id) >= ? ")
                .append("ORDER BY avg_effectiveness DESC, intervention_count DESC");
        params.add(kAnonymityThreshold);

        List<KnowledgeResult.InterventionEffectiveness> rows = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new KnowledgeResult.InterventionEffectiveness(
                        rs.getString("nic_code"),
                        rs.getString("noc_code"),
                        rs.getLong("intervention_count"),
                        rs.getLong("distinct_patients"),
                        rs.getDouble("avg_effectiveness")),
                params.toArray());

        if (!rows.isEmpty()) {
            return KnowledgeResult.of(rows, kAnonymityThreshold);
        }

        // Lista vacía: hay que distinguir "no hay casos previos" de "hay casos pero por
        // debajo del umbral" (FR-CLN-07 exige que la UI muestre mensajes distintos, y
        // desde una lista vacía sola eso es indecidible). Se repregunta SIN el HAVING,
        // contando solo si existe algún grupo — no se traen los datos suprimidos, solo
        // se pregunta por su existencia.
        boolean hayDatosPorDebajoDelUmbral = existsAnyGroupBelowThreshold(schema, query, kAnonymityThreshold);
        return hayDatosPorDebajoDelUmbral
                ? KnowledgeResult.suppressed(kAnonymityThreshold)
                : KnowledgeResult.of(List.of(), kAnonymityThreshold);
    }

    /**
     * ¿Existe algún grupo que la consulta principal descartó por k-anonimato? Devuelve un
     * booleano y nada más — ni los códigos NIC, ni los conteos, ni la efectividad. Saber
     * "hay algo, pero no te lo puedo mostrar" no re-identifica a nadie; saber CUÁNTOS
     * pacientes o QUÉ intervención sí podría.
     */
    private boolean existsAnyGroupBelowThreshold(String schema, KnowledgeQuery query, int threshold) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT EXISTS (SELECT 1 FROM ").append(schema).append(".health_interventions i ")
                .append("JOIN ").append(schema).append(".patients p ON p.id = i.patient_id ")
                .append("WHERE i.effectiveness IS NOT NULL ");

        List<Object> params = new ArrayList<>();
        if (notBlank(query.diagnosisCie10())) {
            sql.append("AND i.diagnosis_cie10 = ? ");
            params.add(query.diagnosisCie10());
        }
        if (notBlank(query.nandaCode())) {
            sql.append("AND i.nanda_code = ? ");
            params.add(query.nandaCode());
        }
        if (query.sex() != null) {
            sql.append("AND p.sex = ? ");
            params.add(query.sex().name());
        }
        sql.append("GROUP BY i.nic_code, i.noc_code ")
                .append("HAVING COUNT(DISTINCT i.patient_id) < ?)");
        params.add(threshold);

        Boolean exists = jdbcTemplate.queryForObject(sql.toString(), Boolean.class, params.toArray());
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Los filtros de edad de FR-CLN-06 no se pueden aplicar hoy: {@code date_of_birth}
     * está cifrada con AES-GCM (AC-09), y un ciphertext no es comparable por rango — no
     * hay forma de escribir "nacidos entre X e Y" en SQL sobre una columna cifrada.
     * Resolverlo requiere una de tres cosas, todas fuera del alcance de esta sub-fase:
     * una columna derivada no reversible (p. ej. banda etaria en claro, que es PHI
     * debilitada y necesita su propio análisis de privacidad), cifrado que preserve el
     * orden (rompe la garantía que AC-09 da hoy), o descifrar y filtrar en memoria (que
     * saca de la base exactamente las filas que k-anonimato busca no exponer).
     *
     * <p>Se lanza en vez de ignorar el filtro en silencio: ignorarlo devolvería un
     * conjunto MÁS AMPLIO que el pedido, presentado como si fuera el pedido — un
     * resultado clínicamente engañoso, y además con más pacientes de los que el usuario
     * creyó estar consultando.
     */
    private String ageFilterUnsupported() {
        return "El filtro por edad no está soportado: date_of_birth está cifrada (AC-09) y no admite "
                + "comparación por rango en SQL. Ver el javadoc de JdbcKnowledgeEngineRepository.";
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Mismo patrón de revalidación en el sink que el resto de los repositorios (AC-05). */
    private String schemaOf(TenantSlug tenantSlug) {
        String slug = tenantSlug.value();
        if (!TenantSlug.PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("TenantSlug rechazado en el sink de KnowledgeEngineRepository: " + slug);
        }
        return PostgresIdentifiers.quote("tenant_" + slug);
    }
}
