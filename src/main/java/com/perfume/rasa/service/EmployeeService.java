package com.perfume.rasa.service;

import com.perfume.rasa.dto.EmployeeRequestDTO;
import com.perfume.rasa.dto.EmployeeResponseDTO;
import com.perfume.rasa.model.Employee;
import com.perfume.rasa.model.User;
import com.perfume.rasa.model.UserProfile;
import com.perfume.rasa.repository.EmployeeRepository;
import com.perfume.rasa.repository.UserProfileRepository;
import com.perfume.rasa.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public EmployeeService(EmployeeRepository employeeRepository, 
                           UserRepository userRepository, 
                           UserProfileRepository userProfileRepository, 
                           PasswordEncoder passwordEncoder,
                           EmailService emailService) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponseDTO> getAllEmployees() {
        return employeeRepository.findAll().stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponseDTO> getFilteredEmployees(
            String search,
            String department,
            User.Role role,
            Pageable pageable
    ) {
        Specification<Employee> spec = Specification.where(null);

        if (search != null && !search.trim().isEmpty()) {
            String q = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                Join<Employee, User> userJoin = root.join("user");
                return cb.or(
                    cb.like(cb.lower(root.get("employeeId")), q),
                    cb.like(cb.lower(root.get("department")), q),
                    cb.like(cb.lower(root.get("designation")), q),
                    cb.like(cb.lower(userJoin.get("fullName")), q),
                    cb.like(cb.lower(userJoin.get("email")), q)
                );
            });
        }

        if (department != null && !department.trim().isEmpty() && !department.equalsIgnoreCase("ALL")) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("department"), department.trim()));
        }

        if (role != null) {
            spec = spec.and((root, query, cb) -> {
                Join<Employee, User> userJoin = root.join("user");
                return cb.equal(userJoin.get("role"), role);
            });
        }

        return employeeRepository.findAll(spec, pageable).map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public EmployeeResponseDTO getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
        return mapToResponseDTO(employee);
    }

    @Transactional
    public EmployeeResponseDTO createEmployee(EmployeeRequestDTO dto) {
        // Validation checks
        if (employeeRepository.existsByEmployeeId(dto.getEmployeeId().trim())) {
            throw new IllegalArgumentException("Employee ID already exists: " + dto.getEmployeeId());
        }
        String email = dto.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }
        if (userRepository.existsByPhone(dto.getPhone())) {
            throw new IllegalArgumentException("Phone number already exists: " + dto.getPhone());
        }
        if (dto.getPassword() == null || dto.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required for new employee");
        }

        // Create and save User first
        User user = new User();
        user.setFullName(dto.getFullName());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setRole(dto.getRole());
        user.setEmailVerified(true); // Pre-verified since created by admin
        User savedUser = userRepository.save(user);

        // Create and save default profile for user
        UserProfile profile = new UserProfile();
        profile.setUser(savedUser);
        profile.setPhoneNumber(dto.getPhone());
        userProfileRepository.save(profile);

        // Create and save Employee
        Employee employee = new Employee();
        employee.setEmployeeId(dto.getEmployeeId().trim());
        employee.setDepartment(dto.getDepartment() != null ? dto.getDepartment().trim() : null);
        employee.setDesignation(dto.getDesignation() != null ? dto.getDesignation().trim() : null);
        employee.setDateOfJoining(dto.getDateOfJoining());
        employee.setUser(savedUser);
        Employee savedEmployee = employeeRepository.save(employee);

        // Send welcome credentials email
        try {
            String loginLink = baseUrl + "/login.html";
            emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getFullName(), savedUser.getEmail(), dto.getPassword().trim(), loginLink);
            log.info("Welcome credentials email sent to employee: {}", savedUser.getEmail());
        } catch (Exception ex) {
            log.error("Failed to send welcome credentials email to employee: {}", ex.getMessage());
        }

        return mapToResponseDTO(savedEmployee);
    }

    @Transactional
    public EmployeeResponseDTO updateEmployee(Long id, EmployeeRequestDTO dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
        User user = employee.getUser();

        // Unique check for employee ID if changed
        String reqEmpId = dto.getEmployeeId().trim();
        if (!employee.getEmployeeId().equalsIgnoreCase(reqEmpId)) {
            throw new IllegalArgumentException("Employee ID cannot be updated.");
        }

        // Unique checks for user fields if changed
        String reqEmail = dto.getEmail().toLowerCase().trim();
        if (!user.getEmail().equalsIgnoreCase(reqEmail) && userRepository.existsByEmail(reqEmail)) {
            throw new IllegalArgumentException("Email already exists: " + reqEmail);
        }
        if (!dto.getPhone().equals(user.getPhone()) && userRepository.existsByPhone(dto.getPhone())) {
            throw new IllegalArgumentException("Phone number already exists: " + dto.getPhone());
        }

        // Update User
        user.setFullName(dto.getFullName());
        user.setEmail(reqEmail);
        user.setPhone(dto.getPhone());
        user.setRole(dto.getRole());
        if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        userRepository.save(user);

        // Update Profile phone number
        userProfileRepository.findByUserId(user.getId()).ifPresent(p -> {
            p.setPhoneNumber(dto.getPhone());
            userProfileRepository.save(p);
        });

        // Update Employee
        employee.setEmployeeId(reqEmpId);
        employee.setDepartment(dto.getDepartment() != null ? dto.getDepartment().trim() : null);
        employee.setDesignation(dto.getDesignation() != null ? dto.getDesignation().trim() : null);
        employee.setDateOfJoining(dto.getDateOfJoining());
        Employee savedEmployee = employeeRepository.save(employee);

        return mapToResponseDTO(savedEmployee);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
        User user = employee.getUser();

        try {
            // Delete Employee first
            employeeRepository.delete(employee);

            // Delete UserProfile
            userProfileRepository.findByUserId(user.getId()).ifPresent(userProfileRepository::delete);

            // Delete User
            userRepository.delete(user);
            
            // Flush changes to verify constraints
            userRepository.flush();
        } catch (Exception e) {
            throw new RuntimeException("Cannot delete employee: The user account has associated records (orders, tickets, etc.).");
        }
    }

    @Transactional
    public void toggleLockStatus(Long id) {
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
        User user = emp.getUser();
        if (user.getRole() == User.Role.SUPERADMIN) {
            throw new IllegalArgumentException("Superadmin account cannot be locked.");
        }
        user.setLocked(!user.isLocked());
        userRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.trim().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
        User user = emp.getUser();
        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        userRepository.save(user);
    }

    private EmployeeResponseDTO mapToResponseDTO(Employee employee) {
        EmployeeResponseDTO dto = new EmployeeResponseDTO();
        dto.setId(employee.getId());
        dto.setEmployeeId(employee.getEmployeeId());
        dto.setDepartment(employee.getDepartment());
        dto.setDesignation(employee.getDesignation());
        dto.setDateOfJoining(employee.getDateOfJoining());

        User user = employee.getUser();
        if (user != null) {
            dto.setUserId(user.getId());
            dto.setFullName(user.getFullName());
            dto.setEmail(user.getEmail());
            dto.setPhone(user.getPhone());
            dto.setRole(user.getRole());
            dto.setLocked(user.isLocked());
        }
        return dto;
    }
}
