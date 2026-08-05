package com.carelink.identity.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    private String email;

    private String role;

    @Column(name = "service_id")
    private String serviceId;

    private boolean active;

    @Column(name = "password")
    private String password;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UserEntity() {}

    public UserEntity(UUID id, UUID tenantId, String email, String role, String serviceId, boolean active,
                       String password, OffsetDateTime createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.role = role;
        this.serviceId = serviceId;
        this.active = active;
        this.password = password;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
