package com.carelink.identity.application.dto;

public class AcceptInvitationRequest {
    private String token;
    private String password;

    public AcceptInvitationRequest() {}

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
