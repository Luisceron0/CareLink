package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.KnowledgeQuery;
import com.carelink.clinical.domain.KnowledgeResult;
import com.carelink.clinical.domain.port.KnowledgeEngineRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FR-CLN-06, FR-CLN-07, ADR-007.
 *
 * <p>El umbral sale de {@code KNOWLEDGE_ANONYMITY_THRESHOLD} (default 5, §5.6 lo declara
 * configurable) pero con un PISO: un umbral menor a 2 desactivaría el k-anonimato por
 * completo —con k=1 cada grupo de un solo paciente pasa— y ADR-007 lo declara "no
 * negociable". Configurar hacia arriba (más estricto) es una decisión operativa legítima;
 * configurar hacia abajo hasta apagarlo no lo es, y una variable de entorno mal puesta no
 * debería poder hacerlo en silencio.
 */
@Component
public class SearchKnowledgeUseCase {

    /** k=2 es el mínimo con el que el agregado sigue siendo un agregado. */
    private static final int MINIMUM_ENFORCEABLE_THRESHOLD = 2;

    private final KnowledgeEngineRepository repository;
    private final int kAnonymityThreshold;

    public SearchKnowledgeUseCase(KnowledgeEngineRepository repository,
                                   @Value("${carelink.knowledge-anonymity-threshold:5}") int configuredThreshold) {
        this.repository = repository;
        if (configuredThreshold < MINIMUM_ENFORCEABLE_THRESHOLD) {
            throw new IllegalStateException(
                    "KNOWLEDGE_ANONYMITY_THRESHOLD=" + configuredThreshold + " desactivaría el k-anonimato. "
                            + "ADR-007 lo declara no negociable; el mínimo es " + MINIMUM_ENFORCEABLE_THRESHOLD + ".");
        }
        this.kAnonymityThreshold = configuredThreshold;
    }

    @Auditable(action = "KNOWLEDGE_SEARCH", tenantSlugExpression = "#tenantSlug.value()")
    public KnowledgeResult execute(TenantSlug tenantSlug, KnowledgeQuery query) {
        return repository.search(tenantSlug, query, kAnonymityThreshold);
    }

    public int threshold() {
        return kAnonymityThreshold;
    }
}
