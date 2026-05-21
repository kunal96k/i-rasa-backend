package com.perfume.rasa.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;

/**
 * Service for handling contact form submissions and ticket management
 * Sends confirmation emails to users and notifications to admin
 */
@Slf4j
@Service
public class ContactTicketService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${spring.mail.username:no-reply@rasaperfumes.in}")
    private String fromEmail;

    @Value("${admin.email:admin@rasaperfumes.in}")
    private String adminEmail;

    /**
     * Create and save a contact ticket
     * @param name Customer name
     * @param email Customer email
     * @param subject Ticket subject
     * @param message Ticket message
     * @return success status
     */
    public boolean submitContactTicket(String name, String email, String subject, String message) {
        try {
            // Validate input
            if (name == null || name.trim().isEmpty() ||
                email == null || email.trim().isEmpty() ||
                subject == null || subject.trim().isEmpty() ||
                message == null || message.trim().isEmpty()) {
                log.warn("Invalid contact form submission with missing fields");
                return false;
            }

            // Email length validation
            if (message.length() > 5000) {
                log.warn("Contact message exceeds maximum length");
                return false;
            }

            // Send confirmation email to user
            sendUserConfirmationEmail(name, email, subject);

            // Send notification to admin
            sendAdminNotificationEmail(name, email, subject, message);

            log.info("Contact ticket submitted successfully from: {} ({})", name, email);
            return true;

        } catch (Exception e) {
            log.error("Error submitting contact ticket", e);
            return false;
        }
    }

    /**
     * Send confirmation email to user
     */
    private void sendUserConfirmationEmail(String name, String email, String subject) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("subject", subject);
            context.setVariable("ticketId", generateTicketId());
            context.setVariable("submittedTime", LocalDateTime.now().toString());

            String htmlContent = templateEngine.process("ticket-confirmation", context);

            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject("Ticket Confirmed - I Rasa Perfumes Support");
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("User confirmation email sent to: {}", email);

        } catch (MessagingException e) {
            log.error("Error sending user confirmation email to: {}", email, e);
        }
    }

    /**
     * Send notification email to admin
     */
    private void sendAdminNotificationEmail(String name, String email, String subject, String message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("email", email);
            context.setVariable("subject", subject);
            context.setVariable("message", message);
            context.setVariable("submittedTime", LocalDateTime.now());
            context.setVariable("ticketId", generateTicketId());

            String htmlContent = templateEngine.process("admin-ticket-notification", context);

            helper.setFrom(fromEmail);
            helper.setTo(adminEmail);
            helper.setSubject("[NEW TICKET] " + subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("Admin notification email sent for ticket from: {}", name);

        } catch (MessagingException e) {
            log.error("Error sending admin notification email", e);
        }
    }

    /**
     * Generate unique ticket ID
     */
    private String generateTicketId() {
        return "TKT-" + System.currentTimeMillis();
    }
}
