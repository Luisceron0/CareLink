package com.carelink.identity.application.dto;

public class InviteUserRequest {
    private String email;
    private String role;
    private String serviceId;

    public InviteUserRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
}
