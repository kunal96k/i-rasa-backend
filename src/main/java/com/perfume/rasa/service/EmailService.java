package com.perfume.rasa.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

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
}
