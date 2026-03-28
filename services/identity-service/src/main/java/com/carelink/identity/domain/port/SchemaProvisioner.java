package com.carelink.identity.domain.port;

public interface SchemaProvisioner {
    void provisionSchema(String tenantSlug);
}
