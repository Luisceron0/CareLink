package com.carelink.identity.application.dto;

/** Request payload to register a tenant and admin user. */
public final class RegisterTenantRequest {

    /** Tenant display name. */
    private String name;

    /** URL-safe tenant slug. */
    private String slug;

    /** Tenant tax identifier. */
    private String taxId;

    /** Tenant admin email. */
    private String adminEmail;

    /** Tenant admin raw password. */
    private String password;

    /** Default constructor for JSON binding. */
    public RegisterTenantRequest() {
    }

    /**
     * Gets tenant name.
     *
     * @return tenant name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets tenant name.
     *
     * @param tenantName tenant name
     */
    public void setName(final String tenantName) {
        this.name = tenantName;
    }

    /**
     * Gets tenant slug.
     *
     * @return tenant slug
     */
    public String getSlug() {
        return slug;
    }

    /**
     * Sets tenant slug.
     *
     * @param tenantSlug tenant slug
     */
    public void setSlug(final String tenantSlug) {
        this.slug = tenantSlug;
    }

    /**
     * Gets tax id.
     *
     * @return tax id
     */
    public String getTaxId() {
        return taxId;
    }

    /**
     * Sets tax id.
     *
     * @param taxIdentifier tax id
     */
    public void setTaxId(final String taxIdentifier) {
        this.taxId = taxIdentifier;
    }

    /**
     * Gets admin email.
     *
     * @return admin email
     */
    public String getAdminEmail() {
        return adminEmail;
    }

    /**
     * Sets admin email.
     *
     * @param tenantAdminEmail admin email
     */
    public void setAdminEmail(final String tenantAdminEmail) {
        this.adminEmail = tenantAdminEmail;
    }

    /**
     * Gets admin password.
     *
     * @return admin password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets admin password.
     *
     * @param adminPassword admin password
     */
    public void setPassword(final String adminPassword) {
        this.password = adminPassword;
    }
}
