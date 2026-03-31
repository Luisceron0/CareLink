package com.carelink.identity.infrastructure.mail;

import com.carelink.identity.domain.port.EmailNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public final class SmtpEmailNotifier implements EmailNotifier {

    /** Spring mail sender. */
    private final JavaMailSender mailSender;

    /** Optional sender email address. */
    private final String from;

    /**
     * Builds SMTP notifier.
     *
     * @param mailSenderValue mail sender
     * @param fromValue optional sender address
     */
    public SmtpEmailNotifier(
            final JavaMailSender mailSenderValue,
            @Value("${spring.mail.username:}") final String fromValue) {
        this.mailSender = mailSenderValue;
        this.from = fromValue;
    }

    @Override
    public void sendVerificationEmail(final String to, final String token) {
        final SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        if (from != null && !from.isEmpty()) {
            msg.setFrom(from);
        }
        msg.setSubject("CareLink - Verify your email");
        msg.setText("Please verify your email using this token: " + token);
        mailSender.send(msg);
    }
}
