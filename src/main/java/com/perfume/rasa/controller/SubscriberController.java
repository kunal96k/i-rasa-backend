package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.model.Subscriber;
import com.perfume.rasa.repository.SubscriberRepository;
import com.perfume.rasa.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> getSubscribers() {
        try {
            List<Subscriber> list = subscriberRepository.findAll();
            return ResponseEntity.ok(new ApiResponse(true, "Subscribers fetched successfully", list));
        } catch (Exception e) {
            log.error("Error fetching subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error fetching subscribers list", null));
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
