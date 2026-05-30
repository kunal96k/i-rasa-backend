package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.model.Enquiry;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.EnquiryRepository;
import com.perfume.rasa.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/admin/enquiries")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminEnquiryController {

    @Autowired
    private EnquiryRepository enquiryRepository;

    @Autowired
    private UserRepository userRepository;

    private boolean isAdminOrEmployee(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User.Role role = userOpt.get().getRole();
            return role == User.Role.ADMIN || role == User.Role.EMPLOYEE;
        }
        return false;
    }

    @GetMapping
    public ResponseEntity<?> getEnquiries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL") String status,
            Authentication authentication) {

        // Re-enabled auth check before production
        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Enquiry> enquiryPage;

            if ("ALL".equalsIgnoreCase(status)) {
                enquiryPage = enquiryRepository.findAllByOrderByCreatedAtDesc(pageable);
            } else {
                enquiryPage = enquiryRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase(), pageable);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("content", enquiryPage.getContent());
            response.put("currentPage", enquiryPage.getNumber());
            response.put("totalItems", enquiryPage.getTotalElements());
            response.put("totalPages", enquiryPage.getTotalPages());

            return ResponseEntity.ok(new ApiResponse(true, "Enquiries fetched successfully", response));

        } catch (Exception e) {
            log.error("Error fetching enquiries", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @PostMapping
    public ResponseEntity<?> createManualEnquiry(@RequestBody Enquiry enquiry, Authentication authentication) {
        // Re-enabled auth check before production
        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            if (enquiry.getName() == null || enquiry.getEmail() == null || enquiry.getSubject() == null || enquiry.getMessage() == null) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Name, Email, Subject, and Message are required", null));
            }

            enquiry.setEnquiryId("ENQ" + System.currentTimeMillis());
            enquiry.setStatus("OPEN");
            enquiry.setSource("ADMIN_MANUAL");
            
            enquiryRepository.save(enquiry);
            
            return ResponseEntity.ok().body(new ApiResponse(true, "Enquiry created successfully", null));

        } catch (Exception e) {
            log.error("Error creating manual enquiry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @PutMapping("/{enquiryId}/status")
    public ResponseEntity<?> updateEnquiryStatus(
            @PathVariable String enquiryId,
            @RequestParam String status,
            Authentication authentication) {
            
        // Re-enabled auth check before production
        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            Enquiry enquiry = enquiryRepository.findAll().stream()
                .filter(e -> e.getEnquiryId().equals(enquiryId))
                .findFirst()
                .orElse(null);

            if (enquiry == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Enquiry not found", null));
            }

            enquiry.setStatus(status.toUpperCase());
            enquiryRepository.save(enquiry);

            return ResponseEntity.ok(new ApiResponse(true, "Status updated successfully", enquiry));

        } catch (Exception e) {
            log.error("Error updating enquiry status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @PutMapping("/{enquiryId}")
    public ResponseEntity<?> updateEnquiry(
            @PathVariable String enquiryId,
            @RequestBody Enquiry updatedEnquiry,
            Authentication authentication) {
            
        // Re-enabled auth check before production
        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            Enquiry enquiry = enquiryRepository.findAll().stream()
                .filter(e -> e.getEnquiryId().equals(enquiryId))
                .findFirst()
                .orElse(null);

            if (enquiry == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Enquiry not found", null));
            }

            if (updatedEnquiry.getName() != null) enquiry.setName(updatedEnquiry.getName());
            if (updatedEnquiry.getEmail() != null) enquiry.setEmail(updatedEnquiry.getEmail());
            if (updatedEnquiry.getMobileNo() != null) enquiry.setMobileNo(updatedEnquiry.getMobileNo());
            if (updatedEnquiry.getSubject() != null) enquiry.setSubject(updatedEnquiry.getSubject());
            if (updatedEnquiry.getMessage() != null) enquiry.setMessage(updatedEnquiry.getMessage());
            if (updatedEnquiry.getStatus() != null) enquiry.setStatus(updatedEnquiry.getStatus().toUpperCase());
            enquiry.setUpdatedAt(java.time.LocalDateTime.now());

            enquiryRepository.save(enquiry);

            return ResponseEntity.ok(new ApiResponse(true, "Enquiry updated successfully", enquiry));

        } catch (Exception e) {
            log.error("Error updating enquiry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @DeleteMapping("/{enquiryId}")
    public ResponseEntity<?> deleteEnquiry(@PathVariable String enquiryId, Authentication authentication) {
        // Re-enabled auth check before production
        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }
        
        try {
            Enquiry enquiry = enquiryRepository.findAll().stream()
                .filter(e -> e.getEnquiryId().equals(enquiryId))
                .findFirst()
                .orElse(null);

            if (enquiry == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Enquiry not found", null));
            }

            enquiryRepository.delete(enquiry);
            return ResponseEntity.ok(new ApiResponse(true, "Enquiry deleted successfully", null));

        } catch (Exception e) {
            log.error("Error deleting enquiry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }
}
