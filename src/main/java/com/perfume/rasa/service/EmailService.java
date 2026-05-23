package com.perfume.rasa.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Value("${spring.mail.username:no-reply@rasaperfumes.in}")
    private String fromEmail;

    @Value("${email.fromName:I Rasa Perfumes}")
    private String fromName;

    public void sendVerificationEmail(String toEmail, String fullName, String verificationLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Verify Your I Rasa Account");

            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("verificationLink", verificationLink);

            String html = templateEngine.process("email/verify-email", context);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Verification email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send verification email. Please try again.");
        }
    }

    public void sendWelcomeEmail(String toEmail, String fullName, String email, String loginLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to I Rasa Perfumes – Your Account is Ready!");

            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("email", email);
            context.setVariable("loginLink", loginLink);

            String html = templateEngine.process("email/welcome-email", context);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Welcome email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(otp + " is your I Rasa verification code");

            String content = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #eee;'>" +
                    "<h2 style='color: #d4af37;'>Verify Your Email</h2>" +
                    "<p>Hello,</p>" +
                    "<p>Thank you for choosing I Rasa Perfumes. Use the following code to complete your registration:</p>" +
                    "<div style='background: #f9f9f9; padding: 20px; text-align: center; font-size: 32px; font-weight: bold; letter-spacing: 5px; color: #333;'>" + otp + "</div>" +
                    "<p>This code will expire in 10 minutes. Please do not share this code with anyone.</p>" +
                    "<p>Best regards,<br>The I Rasa Team</p>" +
                    "</div>";

            helper.setText(content, true);
            mailSender.send(message);
            log.info("OTP email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send verification code. Please try again.");
        }
    }

    /**
     * Sent immediately after order placement — payment still pending verification.
     */
    public void sendOrderReceivedEmail(String toEmail, String fullName, Long orderId,
                                       java.util.List<com.perfume.rasa.dto.OrderItemRequestDTO> items,
                                       java.math.BigDecimal subtotal, java.math.BigDecimal discount,
                                       java.math.BigDecimal shipping, java.math.BigDecimal total,
                                       String paymentMethod, String deliveryAddress, String city,
                                       java.time.LocalDate expectedDeliveryDate,
                                       byte[] pdfBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Order RASA-" + orderId + " Received — I Rasa Perfumes");

            Context ctx = new Context();
            ctx.setVariable("fullName", fullName);
            ctx.setVariable("orderId", orderId);
            ctx.setVariable("subject", "Order Received — Payment Under Verification");
            ctx.setVariable("bodyIntro", "Thank you for your order! We've received it and your payment is under verification.");
            ctx.setVariable("status", "Payment Pending");
            ctx.setVariable("paymentMethod", paymentMethod);
            ctx.setVariable("items", items);
            ctx.setVariable("subtotal", subtotal);
            ctx.setVariable("discount", discount != null ? discount : java.math.BigDecimal.ZERO);
            ctx.setVariable("shipping", shipping != null ? shipping : java.math.BigDecimal.ZERO);
            ctx.setVariable("total", total);
            ctx.setVariable("deliveryAddress", deliveryAddress);
            ctx.setVariable("city", city);
            ctx.setVariable("expectedDeliveryDate", expectedDeliveryDate != null
                    ? expectedDeliveryDate.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) : null);
            ctx.setVariable("statusNote",
                    "Your payment is under verification by our team. Once confirmed, you'll receive an order confirmation email with final delivery details.");

            String html = templateEngine.process("email/order-confirmation", ctx);
            helper.setText(html, true);

            if (pdfBytes != null && pdfBytes.length > 0) {
                helper.addAttachment("invoice_INV-RASA-" + orderId + ".pdf", new org.springframework.core.io.ByteArrayResource(pdfBytes));
            }

            mailSender.send(message);
            log.info("Order received email sent to {} for order #{}", toEmail, orderId);
        } catch (Exception e) {
            log.error("Failed to send order received email for order #{}: {}", orderId, e.getMessage());
        }
    }

    /**
     * Sent when admin marks order as CONFIRMED — payment verified.
     */
    public void sendOrderConfirmedEmail(String toEmail, String fullName, Long orderId,
                                        java.util.List<com.perfume.rasa.dto.OrderItemRequestDTO> items,
                                        java.math.BigDecimal subtotal, java.math.BigDecimal discount,
                                        java.math.BigDecimal shipping, java.math.BigDecimal total,
                                        String paymentMethod, String deliveryAddress, String city,
                                        java.time.LocalDate expectedDeliveryDate,
                                        byte[] pdfBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("🎉 Order RASA-" + orderId + " Confirmed — I Rasa Perfumes");

            Context ctx = new Context();
            ctx.setVariable("fullName", fullName);
            ctx.setVariable("orderId", orderId);
            ctx.setVariable("subject", "Order Confirmed! 🎉");
            ctx.setVariable("bodyIntro", "Great news! Your payment has been verified and your order is now confirmed. We're preparing your fragrance with care.");
            ctx.setVariable("status", "Confirmed ✅");
            ctx.setVariable("paymentMethod", paymentMethod);
            ctx.setVariable("items", items);
            ctx.setVariable("subtotal", subtotal);
            ctx.setVariable("discount", discount != null ? discount : java.math.BigDecimal.ZERO);
            ctx.setVariable("shipping", shipping != null ? shipping : java.math.BigDecimal.ZERO);
            ctx.setVariable("total", total);
            ctx.setVariable("deliveryAddress", deliveryAddress);
            ctx.setVariable("city", city);
            ctx.setVariable("expectedDeliveryDate", expectedDeliveryDate != null
                    ? expectedDeliveryDate.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) : null);
            ctx.setVariable("statusNote",
                    "Your order is confirmed and will be dispatched shortly. You can track your order from your profile page.");

            String html = templateEngine.process("email/order-confirmation", ctx);
            helper.setText(html, true);

            if (pdfBytes != null && pdfBytes.length > 0) {
                helper.addAttachment("invoice_INV-RASA-" + orderId + ".pdf", new org.springframework.core.io.ByteArrayResource(pdfBytes));
            }

            mailSender.send(message);
            log.info("Order confirmed email sent to {} for order #{}", toEmail, orderId);
        } catch (Exception e) {
            log.error("Failed to send order confirmed email for order #{}: {}", orderId, e.getMessage());
        }
    }

    public void sendOrderDeliveredEmail(String toEmail, String fullName, Long orderId,
                                        java.util.List<com.perfume.rasa.dto.OrderItemRequestDTO> items,
                                        java.math.BigDecimal subtotal, java.math.BigDecimal discount,
                                        java.math.BigDecimal shipping, java.math.BigDecimal total,
                                        String paymentMethod, String deliveryAddress, String city,
                                        java.time.LocalDate expectedDeliveryDate,
                                        byte[] pdfBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Your package was delivered! — I Rasa Perfumes");

            Context ctx = new Context();
            ctx.setVariable("fullName", fullName);
            ctx.setVariable("orderId", orderId);
            ctx.setVariable("subject", "Your package was delivered!");
            ctx.setVariable("bodyIntro", "Great news! Your package has been delivered successfully. Thank you for shopping with I Rasa Perfumes.");
            ctx.setVariable("status", "Delivered ✅");
            ctx.setVariable("paymentMethod", paymentMethod);
            ctx.setVariable("items", items);
            ctx.setVariable("subtotal", subtotal);
            ctx.setVariable("discount", discount != null ? discount : java.math.BigDecimal.ZERO);
            ctx.setVariable("shipping", shipping != null ? shipping : java.math.BigDecimal.ZERO);
            ctx.setVariable("total", total);
            ctx.setVariable("deliveryAddress", deliveryAddress);
            ctx.setVariable("city", city);
            ctx.setVariable("expectedDeliveryDate", expectedDeliveryDate != null
                    ? expectedDeliveryDate.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) : null);
            ctx.setVariable("statusNote",
                    "Your order has been delivered. If you have any questions, you can reply to this email or visit your account order history.");

            String html = templateEngine.process("email/order-delivered", ctx);
            helper.setText(html, true);

            if (pdfBytes != null && pdfBytes.length > 0) {
                helper.addAttachment("invoice_INV-RASA-" + orderId + ".pdf", new org.springframework.core.io.ByteArrayResource(pdfBytes));
            }

            mailSender.send(message);
            log.info("Order delivered email sent to {} for order #{}", toEmail, orderId);
        } catch (Exception e) {
            log.error("Failed to send order delivered email for order #{}: {}", orderId, e.getMessage());
        }
    }

    /**
     * Send a simple plain-text email notification.
     */
    public void sendSimpleEmail(String toEmail, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("Simple email sent to {} — subject: {}", toEmail, subject);
        } catch (Exception e) {
            log.error("Failed to send simple email to {}: {}", toEmail, e.getMessage());
        }
    }
}

