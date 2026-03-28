package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.domain.value.TaxId;
import com.carelink.identity.domain.port.TenantRepository;
import com.carelink.identity.domain.exception.TenantAlreadyExistsException;
import java.time.OffsetDateTime;
import java.util.UUID;

public class RegisterTenantUseCase {
    private final TenantRepository tenantRepository;

    public RegisterTenantUseCase(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant execute(String name, String slugStr, String taxIdStr) {
        TenantSlug slug = new TenantSlug(slugStr);
        if (tenantRepository.findBySlug(slug.value()).isPresent()) {
            throw new TenantAlreadyExistsException("Tenant exists");
        }
        TaxId taxId = new TaxId(taxIdStr);
        Tenant tenant = new Tenant(UUID.randomUUID(), name, slug, OffsetDateTime.now());
        tenantRepository.save(tenant);
        return tenant;
    }
}
