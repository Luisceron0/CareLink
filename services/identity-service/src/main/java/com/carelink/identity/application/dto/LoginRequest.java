package com.carelink.identity.application.dto;

/** Request payload for login. */
public final class LoginRequest {

    /** User email. */
    private String email;

    /** User password. */
    private String password;

    /** Default constructor for JSON binding. */
    public LoginRequest() {
    }

    /**
     * Gets email.
     *
     * @return email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets email.
     *
     * @param userEmail email
     */
    public void setEmail(final String userEmail) {
        this.email = userEmail;
    }

    /**
     * Gets password.
     *
     * @return password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets password.
     *
     * @param userPassword password
     */
    public void setPassword(final String userPassword) {
        this.password = userPassword;
    }
}
