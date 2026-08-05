package com.carelink.identity.domain.port;

public interface EmailNotifier {
    void sendVerificationEmail(String to, String token);

    /** FR-ID-02 — invitación a un usuario nuevo con el rol que le asignó el TENANT_ADMIN. */
    void sendInvitationEmail(String to, String token, String role);
}
