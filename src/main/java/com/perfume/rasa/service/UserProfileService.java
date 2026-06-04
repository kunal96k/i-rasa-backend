package com.perfume.rasa.service;

import com.perfume.rasa.dto.ProfileUpdateRequest;
import com.perfume.rasa.dto.UserProfileDTO;
import com.perfume.rasa.model.User;
import com.perfume.rasa.model.UserProfile;
import com.perfume.rasa.repository.UserProfileRepository;
import com.perfume.rasa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final com.perfume.rasa.repository.EmployeeRepository employeeRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public UserProfileService(UserProfileRepository userProfileRepository, 
                              UserRepository userRepository,
                              com.perfume.rasa.repository.EmployeeRepository employeeRepository,
                              org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userProfileRepository = userProfileRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Value("${app.upload.storage-dir:upload}")
    private String uploadDir;

    @Transactional
    public UserProfileDTO getProfile(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> createDefaultProfile(user));
        return mapToDTO(user, profile);
    }

    @Transactional
    public UserProfileDTO updateProfile(String email, ProfileUpdateRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> createDefaultProfile(user));

        if (request.getFirstName() != null && request.getLastName() != null) {
            user.setFullName(request.getFirstName() + " " + request.getLastName());
            userRepository.save(user);
        } else if (request.getFirstName() != null) {
            String[] parts = user.getFullName().split(" ", 2);
            String lastName = parts.length > 1 ? parts[1] : "";
            user.setFullName(request.getFirstName() + " " + lastName);
            userRepository.save(user);
        }

        if (request.getPhoneNumber() != null) profile.setPhoneNumber(request.getPhoneNumber());
        if (request.getBirthDate() != null && !request.getBirthDate().isEmpty()) {
            profile.setBirthDate(LocalDate.parse(request.getBirthDate()));
        }
        if (request.getEmailNotificationsEnabled() != null) profile.setEmailNotificationsEnabled(request.getEmailNotificationsEnabled());
        if (request.getSmsAlertsEnabled() != null) profile.setSmsAlertsEnabled(request.getSmsAlertsEnabled());

        userProfileRepository.save(profile);
        return mapToDTO(user, profile);
    }

    @Transactional
    public String uploadAvatar(String email, MultipartFile file) throws IOException {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> createDefaultProfile(user));

        String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path uploadPath = Paths.get(uploadDir, "avatars");

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String fileUrl = "/api/files/avatars/" + filename;
        profile.setProfileImageUrl(fileUrl);
        userProfileRepository.save(profile);

        return fileUrl;
    }

    private UserProfile createDefaultProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        return userProfileRepository.save(profile);
    }

    private UserProfileDTO mapToDTO(User user, UserProfile profile) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().name());
        
        if (profile != null) {
            dto.setPhoneNumber(profile.getPhoneNumber());
            dto.setBirthDate(profile.getBirthDate());
            dto.setProfileImageUrl(profile.getProfileImageUrl());
            dto.setEmailNotificationsEnabled(profile.getEmailNotificationsEnabled());
            dto.setSmsAlertsEnabled(profile.getSmsAlertsEnabled());
        }

        if (user.getRole() != User.Role.CUSTOMER) {
            employeeRepository.findByUserId(user.getId()).ifPresent(emp -> {
                com.perfume.rasa.dto.EmployeeResponseDTO empDto = new com.perfume.rasa.dto.EmployeeResponseDTO();
                empDto.setId(emp.getId());
                empDto.setEmployeeId(emp.getEmployeeId());
                empDto.setDepartment(emp.getDepartment());
                empDto.setDesignation(emp.getDesignation());
                empDto.setDateOfJoining(emp.getDateOfJoining());
                empDto.setUserId(user.getId());
                empDto.setFullName(user.getFullName());
                empDto.setEmail(user.getEmail());
                empDto.setPhone(user.getPhone());
                empDto.setRole(user.getRole());
                dto.setEmployeeDetails(empDto);
            });
        }
        
        return dto;
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.trim().length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters long.");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect current password.");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        userRepository.save(user);
    }
}
