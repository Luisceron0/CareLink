package com.carelink.identity.domain.port;

/** Port for tenant schema provisioning. */
public interface SchemaProvisioner {

    /**
     * Provisions schema for tenant slug.
     *
     * @param tenantSlug tenant slug
     */
    void provisionSchema(String tenantSlug);
}
