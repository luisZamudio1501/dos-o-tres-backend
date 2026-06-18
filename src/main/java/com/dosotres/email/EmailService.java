package com.dosotres.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.support}")
    private String support;

    @Value("${app.mail.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.password:}") String apiKey) {
        this.mailSender = mailSender;
        this.enabled = !apiKey.isBlank();
        if (!enabled) {
            log.warn("Email not configured (RESEND_API_KEY missing) — sends will be skipped");
        }
    }

    @Async
    public void sendPasswordReset(String to, String token) {
        if (!enabled) {
            log.info("SKIP sendPasswordReset to={} token={}", to, token);
            return;
        }
        String link = baseUrl + "/reset-password?token=" + token;
        String html = """
                <p>Hola,</p>
                <p>Recibimos una solicitud para restablecer tu contraseña en Mateo1819.</p>
                <p><a href="%s" style="background:#0C6B6B;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;">
                   Restablecer contraseña</a></p>
                <p>Este enlace expira en <strong>1 hora</strong>. Si no pediste esto, ignorá este correo.</p>
                """.formatted(link);
        send(to, "Restablecer tu contraseña — Mateo1819", html);
    }

    @Async
    public void sendContact(String fromName, String fromEmail, String message) {
        if (!enabled) {
            log.info("SKIP sendContact from={} <{}>", fromName, fromEmail);
            return;
        }
        String html = """
                <p><strong>Nombre:</strong> %s</p>
                <p><strong>Email:</strong> %s</p>
                <p><strong>Mensaje:</strong></p>
                <p style="white-space:pre-wrap">%s</p>
                """.formatted(fromName, fromEmail, message);
        send(support, "Contacto desde la app — " + fromName, html);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "utf-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Failed to send email to={} subject='{}': {}", to, subject, e.getMessage());
        }
    }
}
