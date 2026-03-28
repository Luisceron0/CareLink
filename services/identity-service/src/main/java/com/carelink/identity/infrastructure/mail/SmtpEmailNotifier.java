package com.carelink.identity.infrastructure.mail;

import com.carelink.identity.domain.port.EmailNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailNotifier implements EmailNotifier {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailNotifier(JavaMailSender mailSender, @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendVerificationEmail(String to, String token) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        if (from != null && !from.isEmpty()) msg.setFrom(from);
        msg.setSubject("CareLink - Verify your email");
        msg.setText("Please verify your email using this token: " + token);
        mailSender.send(msg);
    }
}
