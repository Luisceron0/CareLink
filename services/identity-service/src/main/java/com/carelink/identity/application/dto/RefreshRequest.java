package com.carelink.identity.application.dto;

public class RefreshRequest {
    private String refreshToken;

    public RefreshRequest() {}

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
