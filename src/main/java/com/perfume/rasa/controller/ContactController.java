package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.service.ContactTicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for handling contact form submissions and ticket management
 */
@Slf4j
@RestController
@RequestMapping("/api/contact")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ContactController {

    @Autowired
    private ContactTicketService contactTicketService;

    /**
     * Submit contact form/ticket
     * @param requestBody containing name, email, subject, message
     * @return Response with success status and message
     */
    @PostMapping("/submit-ticket")
    public ResponseEntity<?> submitContactTicket(@RequestBody Map<String, String> requestBody) {
        try {
            String name = requestBody.getOrDefault("name", "").trim();
            String email = requestBody.getOrDefault("email", "").trim();
            String subject = requestBody.getOrDefault("subject", "").trim();
            String message = requestBody.getOrDefault("message", "").trim();

            // Validate inputs
            if (name.isEmpty() || email.isEmpty() || subject.isEmpty() || message.isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse(
                    false,
                    "All fields are required",
                    null
                ));
            }

            // Validate email format
            if (!isValidEmail(email)) {
                return ResponseEntity.badRequest().body(new ApiResponse(
                    false,
                    "Invalid email format",
                    null
                ));
            }

            // Submit ticket
            boolean success = contactTicketService.submitContactTicket(name, email, subject, message);

            if (success) {
                Map<String, Object> response = new HashMap<>();
                response.put("ticketId", "TKT-" + System.currentTimeMillis());
                response.put("submittedAt", System.currentTimeMillis());
                response.put("message", "Ticket submitted! We will contact you via email.");

                log.info("Contact ticket submitted successfully from: {}", email);
                return ResponseEntity.ok().body(new ApiResponse(
                    true,
                    "Ticket submitted successfully. We'll contact you soon.",
                    response
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse(
                    false,
                    "Failed to submit ticket. Please try again later.",
                    null
                ));
            }

        } catch (Exception e) {
            log.error("Error submitting contact ticket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse(
                false,
                "An error occurred while processing your request",
                null
            ));
        }
    }

    /**
     * Validate email format
     */
    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        return email.matches(emailRegex);
    }
}
