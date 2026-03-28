package com.carelink.identity.domain.port;

public interface EmailNotifier {
    void sendVerificationEmail(String to, String token);
}
