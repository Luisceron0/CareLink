package com.carelink.identity.application.dto;

/** Request payload for refresh token operations. */
public final class RefreshRequest {

    /** Refresh token value. */
    private String refreshToken;

    /** Default constructor for JSON binding. */
    public RefreshRequest() {
    }

    /**
     * Gets refresh token.
     *
     * @return refresh token
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Sets refresh token.
     *
     * @param tokenValue refresh token
     */
    public void setRefreshToken(final String tokenValue) {
        this.refreshToken = tokenValue;
    }
}
