package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.dto.RegisterRequest;
import com.perfume.rasa.model.User;
import com.perfume.rasa.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    /** POST /api/auth/register */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {

        try {
            User user = userService.registerUser(request);
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

    /** GET /api/auth/verify-email?token=... */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(
            @RequestParam String token,
            HttpServletRequest request) {

        try {
            userService.verifyEmail(token);
            // Redirect to login with success param
            return ResponseEntity.status(302)
                    .header("Location", "/login?verified=true")
                    .build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(302)
                    .header("Location", "/login?verify-error=" + e.getMessage())
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
            Map<String, Object> userData = Map.of(
                    "fullName", user.getFullName(),
                    "email", user.getEmail(),
                    "phone", user.getPhone(),
                    "role", user.getRole().name());

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
            Map<String, Object> userData = Map.of(
                    "fullName", user.getFullName(),
                    "email", user.getEmail(),
                    "phone", user.getPhone(),
                    "role", user.getRole().name());
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
