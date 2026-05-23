package com.perfume.rasa.service;

import com.perfume.rasa.model.ContactTicket;
import com.perfume.rasa.model.ContactTicketEvent;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.ContactTicketRepository;
import com.perfume.rasa.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Autowired
    private ContactTicketRepository contactTicketRepository;

    @Autowired
    private UserRepository userRepository;

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
        String ticketId = submitContactTicket(name, email, subject, message, null, null);
        return ticketId != null;
    }

    /**
     * Create and save a contact ticket, linking to user and order details
     */
    public String submitContactTicket(String name, String email, String subject, String message, String username, Long orderId) {
        try {
            // Validate input
            if (name == null || name.trim().isEmpty() ||
                email == null || email.trim().isEmpty() ||
                subject == null || subject.trim().isEmpty() ||
                message == null || message.trim().isEmpty()) {
                log.warn("Invalid contact form submission with missing fields");
                return null;
            }

            // Email length validation
            if (message.length() > 5000) {
                log.warn("Contact message exceeds maximum length");
                return null;
            }

            String ticketId = generateTicketId();

            ContactTicket ticket = new ContactTicket();
            ticket.setTicketId(ticketId);
            ticket.setName(name.trim());
            ticket.setEmail(email.trim());
            ticket.setSubject(subject.trim());
            ticket.setMessage(message.trim());
            ticket.setOrderId(orderId);

            if (username != null) {
                Optional<User> userOpt = userRepository.findByEmail(username);
                userOpt.ifPresent(ticket::setUser);
            }

            ContactTicketEvent initialEvent = new ContactTicketEvent(
                ticket,
                "STATUS_UPDATE",
                "OPEN",
                "Support ticket created successfully. Our team will review it shortly."
            );
            ticket.addEvent(initialEvent);

            contactTicketRepository.save(ticket);

            // Send confirmation email to user
            sendUserConfirmationEmail(name, email, subject, ticketId);

            // Send notification to admin
            sendAdminNotificationEmail(name, email, subject, message, ticketId);

            log.info("Contact ticket {} submitted successfully from: {} ({})", ticketId, name, email);
            return ticketId;

        } catch (Exception e) {
            log.error("Error submitting contact ticket", e);
            return null;
        }
    }

    /**
     * Get all tickets for a specific user
     */
    public List<ContactTicket> getTicketsForUser(String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return contactTicketRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    /**
     * Send confirmation email to user
     */
    private void sendUserConfirmationEmail(String name, String email, String subject, String ticketId) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("subject", subject);
            context.setVariable("ticketId", ticketId);
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
    private void sendAdminNotificationEmail(String name, String email, String subject, String message, String ticketId) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("email", email);
            context.setVariable("subject", subject);
            context.setVariable("message", message);
            context.setVariable("submittedTime", LocalDateTime.now());
            context.setVariable("ticketId", ticketId);

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
