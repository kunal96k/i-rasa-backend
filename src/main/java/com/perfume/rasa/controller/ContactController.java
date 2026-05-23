package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.model.ContactTicket;
import com.perfume.rasa.repository.UserRepository;
import com.perfume.rasa.service.ContactTicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
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

    @Autowired
    private UserRepository userRepository;

    /**
     * Submit contact form/ticket
     * @param requestBody containing name, email, subject, message, and optional orderId
     * @return Response with success status and message
     */
    @PostMapping("/submit-ticket")
    public ResponseEntity<?> submitContactTicket(
            @RequestBody Map<String, Object> requestBody,
            Authentication authentication) {
        try {
            String subject = requestBody.containsKey("subject") ? requestBody.get("subject").toString().trim() : "";
            String message = requestBody.containsKey("message") ? requestBody.get("message").toString().trim() : "";
            
            // Allow orderId in request
            Long orderId = null;
            if (requestBody.containsKey("orderId")) {
                Object oId = requestBody.get("orderId");
                if (oId != null && !oId.toString().isEmpty()) {
                    try {
                        orderId = Long.parseLong(oId.toString());
                    } catch (NumberFormatException nfe) {
                        log.warn("Invalid orderId format in ticket submission: {}", oId);
                    }
                }
            }

            String name = "";
            String email = "";
            String username = null;

            if (authentication != null) {
                username = authentication.getName();
                com.perfume.rasa.model.User user = userRepository.findByEmail(username).orElse(null);
                if (user != null) {
                    name = user.getFullName();
                    email = user.getEmail();
                }
            }

            // Fallback to requestBody if not authenticated
            if (name.isEmpty() && requestBody.containsKey("name")) {
                name = requestBody.get("name").toString().trim();
            }
            if (email.isEmpty() && requestBody.containsKey("email")) {
                email = requestBody.get("email").toString().trim();
            }

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
            String ticketId = contactTicketService.submitContactTicket(name, email, subject, message, username, orderId);

            if (ticketId != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("ticketId", ticketId);
                response.put("submittedAt", System.currentTimeMillis());
                response.put("message", "Ticket submitted successfully.");

                log.info("Contact ticket {} submitted successfully from: {}", ticketId, email);
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
     * Get tickets for the logged in user
     */
    @GetMapping("/my-tickets")
    public ResponseEntity<?> getMyTickets(Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse(
                    false,
                    "Unauthorized",
                    null
                ));
            }

            List<ContactTicket> tickets = contactTicketService.getTicketsForUser(authentication.getName());

            return ResponseEntity.ok().body(new ApiResponse(
                true,
                "Tickets retrieved successfully",
                tickets
            ));
        } catch (Exception e) {
            log.error("Error retrieving user tickets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse(
                false,
                "An error occurred while processing your request: " + e.getMessage(),
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
