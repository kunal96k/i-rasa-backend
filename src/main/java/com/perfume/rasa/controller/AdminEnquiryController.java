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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

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

    @Value("${app.upload.storage-dir:upload}")
    private String uploadDir;

    private boolean isAdminOrEmployee(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User.Role role = userOpt.get().getRole();
            return role == User.Role.ADMIN || role == User.Role.EMPLOYEE || role == User.Role.SUPERADMIN;
        }
        return false;
    }

    @GetMapping
    public ResponseEntity<?> getEnquiries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL") String status,
            Authentication authentication) {

        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }

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
        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }

        try {
            if (enquiry.getName() == null || enquiry.getEmail() == null || enquiry.getSubject() == null || enquiry.getMessage() == null) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Name, Email, Subject, and Message are required", null));
            }

            if (enquiry.getSubject().trim().length() > 100 || enquiry.getMessage().trim().length() > 1000) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Subject (max 100) or Message (max 1000) exceeds character limits", null));
            }

            if (!isSafeInput(enquiry.getName()) || !isSafeInput(enquiry.getEmail()) || !isSafeInput(enquiry.getSubject()) || !isSafeInput(enquiry.getMessage())) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Input contains unsafe scripting or HTML tags", null));
            }

            enquiry.setName(org.springframework.web.util.HtmlUtils.htmlEscape(enquiry.getName().trim()));
            enquiry.setEmail(org.springframework.web.util.HtmlUtils.htmlEscape(enquiry.getEmail().trim()));
            enquiry.setSubject(org.springframework.web.util.HtmlUtils.htmlEscape(enquiry.getSubject().trim()));
            enquiry.setMessage(org.springframework.web.util.HtmlUtils.htmlEscape(enquiry.getMessage().trim()));
            if (enquiry.getMobileNo() != null) {
                enquiry.setMobileNo(org.springframework.web.util.HtmlUtils.htmlEscape(enquiry.getMobileNo().trim()));
            }

            enquiry.setEnquiryId("ENQ" + System.currentTimeMillis());
            if (enquiry.getStatus() == null || enquiry.getStatus().trim().isEmpty()) {
                enquiry.setStatus("OPEN");
            } else {
                enquiry.setStatus(enquiry.getStatus().trim().toUpperCase());
            }
            enquiry.setSource("ADMIN_MANUAL");
            
            enquiryRepository.save(enquiry);
            
            return ResponseEntity.ok().body(new ApiResponse(true, "Enquiry created successfully", null));

        } catch (Exception e) {
            log.error("Error creating manual enquiry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadEnquiryFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "File is empty", null));
            }

            java.io.File dir = new java.io.File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filename = java.util.UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            java.nio.file.Path path = java.nio.file.Paths.get(uploadDir, filename);
            java.nio.file.Files.write(path, file.getBytes());

            String fileUrl = "/api/files/" + filename;
            return ResponseEntity.ok(new ApiResponse(true, "File uploaded successfully", Map.of("url", fileUrl)));
        } catch (Exception e) {
            log.error("File upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Upload failed: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{enquiryId}/status")
    public ResponseEntity<?> updateEnquiryStatus(
            @PathVariable String enquiryId,
            @RequestParam String status,
            Authentication authentication) {
            
        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }

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
            
        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }

        try {
            Enquiry enquiry = enquiryRepository.findAll().stream()
                .filter(e -> e.getEnquiryId().equals(enquiryId))
                .findFirst()
                .orElse(null);

            if (enquiry == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Enquiry not found", null));
            }

            if (updatedEnquiry.getName() != null) {
                if (!isSafeInput(updatedEnquiry.getName())) return ResponseEntity.badRequest().body(new ApiResponse(false, "Unsafe input detected in Name", null));
                enquiry.setName(org.springframework.web.util.HtmlUtils.htmlEscape(updatedEnquiry.getName().trim()));
            }
            if (updatedEnquiry.getEmail() != null) {
                if (!isSafeInput(updatedEnquiry.getEmail())) return ResponseEntity.badRequest().body(new ApiResponse(false, "Unsafe input detected in Email", null));
                enquiry.setEmail(org.springframework.web.util.HtmlUtils.htmlEscape(updatedEnquiry.getEmail().trim()));
            }
            if (updatedEnquiry.getMobileNo() != null) {
                if (!isSafeInput(updatedEnquiry.getMobileNo())) return ResponseEntity.badRequest().body(new ApiResponse(false, "Unsafe input detected in Mobile", null));
                enquiry.setMobileNo(org.springframework.web.util.HtmlUtils.htmlEscape(updatedEnquiry.getMobileNo().trim()));
            }
            if (updatedEnquiry.getSubject() != null) {
                if (updatedEnquiry.getSubject().trim().length() > 100) return ResponseEntity.badRequest().body(new ApiResponse(false, "Subject must not exceed 100 characters", null));
                if (!isSafeInput(updatedEnquiry.getSubject())) return ResponseEntity.badRequest().body(new ApiResponse(false, "Unsafe input detected in Subject", null));
                enquiry.setSubject(org.springframework.web.util.HtmlUtils.htmlEscape(updatedEnquiry.getSubject().trim()));
            }
            if (updatedEnquiry.getMessage() != null) {
                if (updatedEnquiry.getMessage().trim().length() > 1000) return ResponseEntity.badRequest().body(new ApiResponse(false, "Message must not exceed 1000 characters", null));
                if (!isSafeInput(updatedEnquiry.getMessage())) return ResponseEntity.badRequest().body(new ApiResponse(false, "Unsafe input detected in Message", null));
                enquiry.setMessage(org.springframework.web.util.HtmlUtils.htmlEscape(updatedEnquiry.getMessage().trim()));
            }
            if (updatedEnquiry.getStatus() != null) enquiry.setStatus(updatedEnquiry.getStatus().toUpperCase());
            if (updatedEnquiry.getImageUrl() != null) enquiry.setImageUrl(updatedEnquiry.getImageUrl());
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
        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }
        
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

    private boolean isSafeInput(String value) {
        if (value == null) return true;
        String val = value.trim();
        if (val.contains("<") || val.contains(">")) return false;
        if (val.toLowerCase().contains("javascript:") || val.toLowerCase().matches("(?i).*on\\w+\\s*=.*")) return false;
        return true;
    }
}
