package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.model.ContactTicket;
import com.perfume.rasa.model.Enquiry;
import com.perfume.rasa.repository.EnquiryRepository;
import com.perfume.rasa.repository.UserRepository;
import com.perfume.rasa.service.ContactTicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for handling contact form submissions and ticket management.
 * - /submit-ticket  → saves to ContactTicket (for logged-in users' support tickets)
 * - /submit-enquiry → saves to Enquiry table (for public contact form, shown in admin Enquiries module)
 */
@Slf4j
@RestController
@RequestMapping("/api/contact")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ContactController {

    @Autowired
    private EnquiryRepository enquiryRepository;

    @Autowired
    private ContactTicketService contactTicketService;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.upload.storage-dir:upload}")
    private String uploadDir;

    /**
     * Submit support ticket (requires login or email).
     * Saves to ContactTicket table.
     */
    @PostMapping("/submit-ticket")
    public ResponseEntity<?> submitContactTicket(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String mobileNo,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) MultipartFile imageFile,
            Authentication authentication) {
        try {
            String username = null;
            if (authentication != null && authentication.isAuthenticated()
                    && !authentication.getPrincipal().equals("anonymousUser")) {
                username = authentication.getName();
            } else if (email != null && !email.trim().isEmpty()) {
                username = email;
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse(
                    false, "Please login to submit a ticket, or provide an email address", null));
            }

            // Validate inputs
            if (name == null || name.trim().isEmpty()
                    || email == null || email.trim().isEmpty()
                    || subject == null || subject.trim().isEmpty()
                    || message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse(
                    false, "All fields are required", null));
            }

            if (subject.trim().length() > 100 || message.trim().length() > 1000) {
                return ResponseEntity.badRequest().body(new ApiResponse(
                    false, "Subject (max 100) or Message (max 1000) exceeds character limits", null));
            }

            if (!isSafeInput(name) || !isSafeInput(email) || !isSafeInput(subject) || !isSafeInput(message) || !isSafeInput(mobileNo)) {
                return ResponseEntity.badRequest().body(new ApiResponse(
                    false, "Input contains unsafe scripting or HTML tags", null));
            }

            // HTML-escape inputs to prevent stored XSS
            String safeName = org.springframework.web.util.HtmlUtils.htmlEscape(name.trim());
            String safeEmail = org.springframework.web.util.HtmlUtils.htmlEscape(email.trim());
            String safeSubject = org.springframework.web.util.HtmlUtils.htmlEscape(subject.trim());
            String safeMessage = org.springframework.web.util.HtmlUtils.htmlEscape(message.trim());
            String safeMobileNo = mobileNo != null ? org.springframework.web.util.HtmlUtils.htmlEscape(mobileNo.trim()) : null;

            // Validate email format
            if (!isValidEmail(safeEmail)) {
                return ResponseEntity.badRequest().body(new ApiResponse(
                    false, "Invalid email format", null));
            }

            // Submit ticket via service
            String ticketId = contactTicketService.submitContactTicket(
                    safeName, safeEmail, safeMobileNo, safeSubject, safeMessage, null, username, orderId);

            if (ticketId != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("ticketId", ticketId);
                response.put("submittedAt", System.currentTimeMillis());
                log.info("Contact ticket {} submitted successfully from: {}", ticketId, email);
                return ResponseEntity.ok().body(new ApiResponse(
                    true, "Ticket submitted successfully. We'll contact you soon.", response));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse(
                    false, "Failed to submit ticket. Please try again later.", null));
            }

        } catch (Exception e) {
            log.error("Error submitting contact ticket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse(
                false, "An error occurred while processing your request", null));
        }
    }

    /**
     * Submit public enquiry (no login required).
     * Saves to Enquiry table — visible in the Admin Enquiries dashboard.
     */
    @PostMapping("/submit-enquiry")
    public ResponseEntity<?> submitEnquiry(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String mobileNo,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String message) {
        try {
            if (name == null || name.trim().isEmpty()
                    || email == null || email.trim().isEmpty()
                    || subject == null || subject.trim().isEmpty()
                    || message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse(
                    false, "Name, Email, Subject, and Message are required", null));
            }

            if (subject.trim().length() > 100 || message.trim().length() > 1000) {
                return ResponseEntity.badRequest().body(new ApiResponse(
                    false, "Subject (max 100) or Message (max 1000) exceeds character limits", null));
            }

            if (!isSafeInput(name) || !isSafeInput(email) || !isSafeInput(subject) || !isSafeInput(message) || !isSafeInput(mobileNo)) {
                return ResponseEntity.badRequest().body(new ApiResponse(
                    false, "Input contains unsafe scripting or HTML tags", null));
            }

            Enquiry enquiry = new Enquiry();
            enquiry.setEnquiryId("ENQ" + System.currentTimeMillis());
            enquiry.setName(org.springframework.web.util.HtmlUtils.htmlEscape(name.trim()));
            enquiry.setEmail(org.springframework.web.util.HtmlUtils.htmlEscape(email.trim()));
            enquiry.setMobileNo(mobileNo != null ? org.springframework.web.util.HtmlUtils.htmlEscape(mobileNo.trim()) : null);
            enquiry.setSubject(org.springframework.web.util.HtmlUtils.htmlEscape(subject.trim()));
            enquiry.setMessage(org.springframework.web.util.HtmlUtils.htmlEscape(message.trim()));
            enquiry.setStatus("OPEN");
            enquiry.setSource("WEBSITE");

            enquiryRepository.save(enquiry);

            return ResponseEntity.ok(new ApiResponse(
                true, "Thank you for contacting us! We will get back to you soon.", null));

        } catch (Exception e) {
            log.error("Error submitting enquiry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse(
                false, "An error occurred while processing your request", null));
        }
    }

    /**
     * Get tickets for the logged-in user with pagination.
     */
    @GetMapping("/my-tickets")
    public ResponseEntity<?> getMyTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse(
                    false, "Unauthorized", null));
            }

            Page<ContactTicket> ticketsPage = contactTicketService.getTicketsForUserPaginated(
                    authentication.getName(), page, size);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("content", ticketsPage.getContent());
            responseData.put("totalPages", ticketsPage.getTotalPages());
            responseData.put("totalElements", ticketsPage.getTotalElements());
            responseData.put("currentPage", ticketsPage.getNumber());
            responseData.put("size", ticketsPage.getSize());

            return ResponseEntity.ok().body(new ApiResponse(true, "Tickets retrieved successfully", responseData));

        } catch (Exception e) {
            log.error("Error retrieving user tickets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse(
                false, "An error occurred while processing your request: " + e.getMessage(), null));
        }
    }

    /**
     * Delete a specific ticket.
     */
    @DeleteMapping("/ticket/{ticketId}")
    public ResponseEntity<?> deleteTicket(@PathVariable String ticketId, Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse(
                    false, "Unauthorized", null));
            }

            boolean deleted = contactTicketService.deleteTicket(authentication.getName(), ticketId);
            if (deleted) {
                return ResponseEntity.ok().body(new ApiResponse(true, "Ticket deleted successfully", null));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(
                    false, "Ticket not found or unauthorized", null));
            }

        } catch (Exception e) {
            log.error("Error deleting ticket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse(
                false, "An error occurred while deleting the ticket", null));
        }
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    private boolean isSafeInput(String value) {
        if (value == null) return true;
        String val = value.trim();
        if (val.contains("<") || val.contains(">")) return false;
        if (val.toLowerCase().contains("javascript:") || val.toLowerCase().matches("(?i).*on\\w+\\s*=.*")) return false;
        return true;
    }
}
