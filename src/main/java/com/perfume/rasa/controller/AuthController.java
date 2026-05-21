package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.dto.RegisterRequest;
import com.perfume.rasa.model.User;
import com.perfume.rasa.service.UserService;
import com.perfume.rasa.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final OtpService otpService;
    private final com.perfume.rasa.service.UserProfileService userProfileService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserService userService, OtpService otpService, com.perfume.rasa.service.UserProfileService userProfileService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.otpService = otpService;
        this.userProfileService = userProfileService;
        this.authenticationManager = authenticationManager;
    }

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /** POST /api/auth/register */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        // Check if email was verified via OtpService
        if (!otpService.isEmailVerified(request.getEmail())) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, 
                "Please verify your email via OTP before registering."));
        }

        try {
            User user = userService.registerUser(request);
            // Clear verification after success
            otpService.clearVerification(request.getEmail());
            return ResponseEntity.ok(new ApiResponse(true,
                    "Registration successful! Please check your email to verify your account."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Registration failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(new ApiResponse(false,
                    "Registration failed. Please try again."));
        }
    }

    /** POST /api/auth/send-otp */
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse> sendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("Request to send OTP for email: {}", email);
        
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Email is required"));
        }

        if (userService.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "An account with this email already exists."));
        }

        try {
            String otp = String.valueOf((int) (Math.random() * 900000) + 100000);
            otpService.storeOtp(email, otp);
            log.info("OTP generated and stored for email: {}", email);
            
            userService.sendOtpEmail(email, otp);
            return ResponseEntity.ok(new ApiResponse(true, "OTP sent to " + email));
        } catch (Exception e) {
            log.error("Failed to send OTP to {}: {}", email, e.getMessage());
            return ResponseEntity.internalServerError().body(new ApiResponse(false, "Failed to send OTP."));
        }
    }

    /** POST /api/auth/verify-otp */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse> verifyOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");
        
        log.info("Verifying OTP for {}: {}", email, otp);

        if (otpService.verifyOtp(email, otp)) {
            log.info("OTP verified successfully for: {}", email);
            return ResponseEntity.ok(new ApiResponse(true, "OTP verified successfully!"));
        } else {
            log.warn("OTP verification failed for: {}", email);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Invalid or expired OTP."));
        }
    }

    /** GET /api/auth/verify-email?token=... */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(
            @RequestParam String token,
            HttpServletRequest request) {

        try {
            userService.verifyEmail(token);
            // Redirect to login with success param
            return ResponseEntity.status(302)
                    .header("Location", baseUrl + "/login.html?verified=true")
                    .build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(302)
                    .header("Location", baseUrl + "/login.html?verify-error=" + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error during email verification: ", e);
            return ResponseEntity.status(302)
                    .header("Location", baseUrl + "/login.html?verify-error=An unexpected error occurred. Please try again.")
                    .build();
        }
    }

    /** POST /api/auth/login — JSON-based login for the frontend */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(
            @RequestBody Map<String, String> credentials,
            HttpServletRequest request) {

        String email = credentials.get("email");
        String password = credentials.get("password");

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            SecurityContextHolder.getContext().setAuthentication(auth);

            // Save authentication to session
            HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());

            User user = userService.getUserByEmail(email);
            String profileImageUrl = null;
            try {
                profileImageUrl = userProfileService.getProfile(email).getProfileImageUrl();
            } catch(Exception e) {}

            Map<String, Object> userData = new java.util.HashMap<>();
            userData.put("id", user.getId());
            userData.put("fullName", user.getFullName());
            userData.put("email", user.getEmail());
            userData.put("phone", user.getPhone() != null ? user.getPhone() : "");
            userData.put("role", user.getRole().name());
            userData.put("profileImageUrl", profileImageUrl != null ? profileImageUrl : "");

            return ResponseEntity.ok(new ApiResponse(true, "Login successful", userData));

        } catch (org.springframework.security.authentication.DisabledException e) {
            return ResponseEntity.status(403).body(new ApiResponse(false,
                    "Please verify your email address before logging in."));
        } catch (Exception e) {
            log.warn("Login failed for {}: {}", email, e.getMessage());
            return ResponseEntity.status(401).body(new ApiResponse(false,
                    "Invalid email or password."));
        }
    }

    /** GET /api/auth/me — Get current session user */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Not authenticated"));
        }

        try {
            User user = userService.getUserByEmail(auth.getName());
            String profileImageUrl = null;
            try {
                profileImageUrl = userProfileService.getProfile(auth.getName()).getProfileImageUrl();
            } catch(Exception e) {}

            Map<String, Object> userData = new java.util.HashMap<>();
            userData.put("id", user.getId());
            userData.put("fullName", user.getFullName());
            userData.put("email", user.getEmail());
            userData.put("phone", user.getPhone() != null ? user.getPhone() : "");
            userData.put("role", user.getRole().name());
            userData.put("profileImageUrl", profileImageUrl != null ? profileImageUrl : "");
            return ResponseEntity.ok(new ApiResponse(true, "Authenticated", userData));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Not authenticated"));
        }
    }

    /** POST /api/auth/logout */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null)
            session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(new ApiResponse(true, "Logged out successfully"));
    }
}
