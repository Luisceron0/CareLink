package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.KnowledgeQuery;
import com.carelink.clinical.domain.KnowledgeResult;
import com.carelink.identity.domain.value.TenantSlug;

public interface KnowledgeEngineRepository {

    /**
     * FR-CLN-06 + FR-CLN-07. El umbral de k-anonimato se aplica DENTRO de la consulta
     * (un {@code HAVING}), no filtrando en memoria lo que la base ya devolvió: las filas
     * por debajo del umbral nunca salen de PostgreSQL.
     */
    KnowledgeResult search(TenantSlug tenantSlug, KnowledgeQuery query, int kAnonymityThreshold);
}
