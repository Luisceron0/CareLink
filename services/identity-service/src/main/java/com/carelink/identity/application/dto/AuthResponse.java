package com.carelink.identity.application.dto;

/** Response payload for authentication endpoints. */
public final class AuthResponse {

    /** JWT access token. */
    private String accessToken;

    /** Default constructor for JSON binding. */
    public AuthResponse() {
    }

    /**
     * Builds response with access token.
     *
     * @param tokenValue access token
     */
    public AuthResponse(final String tokenValue) {
        this.accessToken = tokenValue;
    }

    /**
     * Gets access token.
     *
     * @return access token
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Sets access token.
     *
     * @param tokenValue access token
     */
    public void setAccessToken(final String tokenValue) {
        this.accessToken = tokenValue;
    }
}
