package com.perfume.rasa.service;

import com.perfume.rasa.dto.RegisterRequest;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isEmailVerified(), // account enabled only when verified
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    @Transactional
    public User registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setEmailVerified(false);

        // Generate verification token
        String token = UUID.randomUUID().toString();
        user.setEmailVerificationToken(token);
        user.setEmailVerificationExpiry(LocalDateTime.now().plusHours(24));

        userRepository.save(user);

        // Send verification email
        String verificationLink = baseUrl + "/api/auth/verify-email?token=" + token;
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verificationLink);

        log.info("New user registered: {}", user.getEmail());
        return user;
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification link."));

        if (user.getEmailVerificationExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification link has expired. Please register again.");
        }

        if (user.isEmailVerified()) {
            return; // already verified, idempotent
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiry(null);
        userRepository.save(user);

        // Send welcome email
        String loginLink = baseUrl + "/login";
        emailService.sendWelcomeEmail(user.getEmail(), user.getFullName(), user.getEmail(), loginLink);

        log.info("Email verified for user: {}", user.getEmail());
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
