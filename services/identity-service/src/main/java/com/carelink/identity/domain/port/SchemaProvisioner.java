package com.carelink.identity.domain.port;

import com.carelink.identity.domain.value.TenantSlug;

/**
 * AC-05: el port acepta {@code TenantSlug}, no {@code String}. Un slug que llega
 * hasta acá ya pasó por la validación del value object — la invariante de formato
 * vive en el tipo, no en la disciplina de cada caller (lección de ADR-010: un
 * segundo caller sin esa disciplina fue exactamente lo que rompió esto la primera
 * vez).
 */
public interface SchemaProvisioner {
    void provisionSchema(TenantSlug tenantSlug);
}
