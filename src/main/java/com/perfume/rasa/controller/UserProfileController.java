package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.dto.ProfileUpdateRequest;
import com.perfume.rasa.dto.UserProfileDTO;
import com.perfume.rasa.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse> getProfile(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized", null));
        }
        UserProfileDTO profile = userProfileService.getProfile(auth.getName());
        return ResponseEntity.ok(new ApiResponse(true, "Profile fetched", profile));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse> updateProfile(Authentication auth, @RequestBody ProfileUpdateRequest request) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized", null));
        }
        UserProfileDTO profile = userProfileService.updateProfile(auth.getName(), request);
        return ResponseEntity.ok(new ApiResponse(true, "Profile updated successfully", profile));
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<ApiResponse> uploadAvatar(Authentication auth, @RequestParam("file") MultipartFile file) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized", null));
        }
        try {
            String url = userProfileService.uploadAvatar(auth.getName(), file);
            return ResponseEntity.ok(new ApiResponse(true, "Avatar uploaded successfully", url));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "Failed to upload avatar: " + e.getMessage(), null));
        }
    }
}
