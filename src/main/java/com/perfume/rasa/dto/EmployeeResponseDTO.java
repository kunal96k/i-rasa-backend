package com.perfume.rasa.dto;

import com.perfume.rasa.model.User;
import java.math.BigDecimal;
import java.time.LocalDate;

public class EmployeeResponseDTO {
    private Long id;
    private String employeeId;
    private String department;
    private String designation;
    private LocalDate dateOfJoining;
    
    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private User.Role role;
    private boolean locked;

    public EmployeeResponseDTO() {}

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public LocalDate getDateOfJoining() { return dateOfJoining; }
    public void setDateOfJoining(LocalDate dateOfJoining) { this.dateOfJoining = dateOfJoining; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public User.Role getRole() { return role; }
    public void setRole(User.Role role) { this.role = role; }
}
