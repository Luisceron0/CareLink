package com.carelink.identity.domain.port;

/** Port for outbound email notifications. */
public interface EmailNotifier {

    /**
     * Sends verification email.
     *
     * @param to recipient email
     * @param token verification token
     */
    void sendVerificationEmail(String to, String token);
}
