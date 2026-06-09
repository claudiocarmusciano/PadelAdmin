package com.padeladmin.padeladmin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío de emails (Gmail SMTP). Si el mail no está configurado (app.mail.enabled=false o sin
 * remitente), queda desactivado y los métodos devuelven false → el caller usa un fallback
 * (ej: mostrar la contraseña en pantalla).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${app.mail.from:}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.from = from;
    }

    public boolean isEnabled() {
        return enabled && from != null && !from.isBlank() && mailSenderProvider.getIfAvailable() != null;
    }

    /** Email con la contraseña inicial del club. Devuelve true si se envió. */
    public boolean sendClubWelcome(String toEmail, String clubName, String password, String loginUrl) {
        String subject = "PadelAdmin — Acceso de tu club \"" + clubName + "\"";
        String body = "Hola,\n\n"
                + "Se creó el acceso para administrar el club \"" + clubName + "\" en PadelAdmin.\n\n"
                + "Usuario (email): " + toEmail + "\n"
                + "Contraseña inicial: " + password + "\n\n"
                + "Al ingresar por primera vez se te pedirá cambiar la contraseña.\n"
                + (loginUrl != null && !loginUrl.isBlank() ? "Ingresá en: " + loginUrl + "\n\n" : "\n")
                + "Saludos,\nPadelAdmin";
        return send(toEmail, subject, body);
    }

    private boolean send(String to, String subject, String body) {
        if (!isEnabled()) {
            log.info("Email desactivado (app.mail.enabled=false o sin remitente): no se envía a {}", to);
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSenderProvider.getObject().send(msg);
            log.info("Email enviado a {}", to);
            return true;
        } catch (Exception ex) {
            log.warn("No se pudo enviar email a {}: {}", to, ex.getMessage());
            return false;
        }
    }
}
