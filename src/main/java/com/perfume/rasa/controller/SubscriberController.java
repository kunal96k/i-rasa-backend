package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.model.Subscriber;
import com.perfume.rasa.repository.SubscriberRepository;
import com.perfume.rasa.repository.UserRepository;
import com.perfume.rasa.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/subscribe")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SubscriberController {

    @Autowired
    private SubscriberRepository subscriberRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    private boolean isAdminOrEmployee(Authentication authentication) {
        if (authentication == null) return false;
        Optional<com.perfume.rasa.model.User> userOpt = userRepository.findByEmail(authentication.getName());
        if (userOpt.isPresent()) {
            com.perfume.rasa.model.User.Role role = userOpt.get().getRole();
            return role == com.perfume.rasa.model.User.Role.ADMIN || role == com.perfume.rasa.model.User.Role.EMPLOYEE || role == com.perfume.rasa.model.User.Role.SUPERADMIN;
        }
        return false;
    }

    /**
     * Public endpoint to subscribe to newsletter.
     */
    @PostMapping
    public ResponseEntity<?> subscribe(@RequestParam String email) {
        try {
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Email address is required", null));
            }

            String cleanEmail = email.trim().toLowerCase();
            if (!cleanEmail.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Invalid email format", null));
            }

            Optional<Subscriber> existingOpt = subscriberRepository.findByEmail(cleanEmail);
            if (existingOpt.isPresent()) {
                Subscriber sub = existingOpt.get();
                if (sub.isStatus()) {
                    return ResponseEntity.ok(new ApiResponse(true, "You are already subscribed!", null));
                } else {
                    sub.setStatus(true);
                    subscriberRepository.save(sub);
                    return ResponseEntity.ok(new ApiResponse(true, "Subscription re-activated successfully!", null));
                }
            }

            Subscriber subscriber = new Subscriber();
            subscriber.setEmail(cleanEmail);
            subscriber.setStatus(true);
            subscriberRepository.save(subscriber);

            return ResponseEntity.ok(new ApiResponse(true, "Thank you for subscribing to our newsletter!", null));
        } catch (Exception e) {
            log.error("Error in newsletter subscription", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "An error occurred while processing your subscription", null));
        }
    }

    /**
     * Admin endpoint to get list of all subscribers.
     */
    @GetMapping("/list")
    public ResponseEntity<?> getSubscribers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            Authentication authentication) {

        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }

        try {
            String normalizedSortBy = "createdAt";
            if (java.util.Arrays.asList("id", "email", "createdAt", "status").contains(sortBy)) {
                normalizedSortBy = sortBy;
            }
            Sort.Direction direction = "ASC".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, normalizedSortBy));

            Specification<Subscriber> spec = Specification.where(null);

            if (!"ALL".equalsIgnoreCase(status)) {
                boolean activeVal = "ON".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status) || "TRUE".equalsIgnoreCase(status);
                spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), activeVal));
            }

            if (search != null && !search.trim().isEmpty()) {
                String q = "%" + search.trim().toLowerCase() + "%";
                spec = spec.and((root, query, cb) -> {
                    var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    predicates.add(cb.like(cb.lower(root.get("email")), q));
                    try {
                        Long idVal = Long.parseLong(search.trim());
                        predicates.add(cb.equal(root.get("id"), idVal));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                    return cb.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                });
            }

            Page<Subscriber> subscribersPage = subscriberRepository.findAll(spec, pageable);

            Map<String, Object> data = Map.of(
                    "content", subscribersPage.getContent(),
                    "currentPage", subscribersPage.getNumber(),
                    "totalItems", subscribersPage.getTotalElements(),
                    "totalPages", subscribersPage.getTotalPages(),
                    "size", subscribersPage.getSize(),
                    "sortBy", normalizedSortBy,
                    "sortOrder", direction.name()
            );

            return ResponseEntity.ok(new ApiResponse(true, "Subscribers fetched successfully", data));
        } catch (Exception e) {
            log.error("Error fetching subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error fetching subscribers list: " + e.getMessage(), null));
        }
    }

    /**
     * Admin endpoint to toggle subscriber active/inactive status (ON/OFF).
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggleSubscriberStatus(@PathVariable Long id) {
        try {
            Optional<Subscriber> subOpt = subscriberRepository.findById(id);
            if (subOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse(false, "Subscriber not found", null));
            }

            Subscriber sub = subOpt.get();
            sub.setStatus(!sub.isStatus());
            subscriberRepository.save(sub);

            return ResponseEntity.ok(new ApiResponse(true, "Status updated to " + (sub.isStatus() ? "ON" : "OFF"), sub));
        } catch (Exception e) {
            log.error("Error toggling subscriber status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error updating status", null));
        }
    }

    /**
     * Admin endpoint to delete subscriber.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSubscriber(@PathVariable Long id) {
        try {
            if (!subscriberRepository.existsById(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse(false, "Subscriber not found", null));
            }
            subscriberRepository.deleteById(id);
            return ResponseEntity.ok(new ApiResponse(true, "Subscriber deleted successfully", null));
        } catch (Exception e) {
            log.error("Error deleting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error deleting subscriber", null));
        }
    }

    /**
     * Admin endpoint to send a newsletter email to all active subscribers.
     */
    @PostMapping("/send-newsletter")
    public ResponseEntity<?> sendNewsletter(@RequestBody Map<String, String> payload) {
        try {
            String subject = payload.get("subject");
            String message = payload.get("message");

            if (subject == null || subject.trim().isEmpty() || message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Subject and Message body are required", null));
            }

            List<Subscriber> activeSubscribers = subscriberRepository.findByStatus(true);
            if (activeSubscribers.isEmpty()) {
                return ResponseEntity.ok(new ApiResponse(true, "No active subscribers to send email to", null));
            }

            // Send simple email in a separate thread loop (or direct loop)
            new Thread(() -> {
                log.info("Starting newsletter dispatch of '{}' to {} subscribers", subject, activeSubscribers.size());
                for (Subscriber sub : activeSubscribers) {
                    try {
                        emailService.sendSimpleEmail(sub.getEmail(), subject, message);
                        Thread.sleep(100); // Small sleep to prevent rate-limiting/spam flags
                    } catch (Exception e) {
                        log.error("Failed to send newsletter to {}", sub.getEmail(), e);
                    }
                }
                log.info("Newsletter dispatch completed.");
            }).start();

            return ResponseEntity.ok(new ApiResponse(true, "Newsletter transmission initiated successfully to " + activeSubscribers.size() + " subscribers.", null));
        } catch (Exception e) {
            log.error("Error sending newsletter", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error executing newsletter sending", null));
        }
    }
}
