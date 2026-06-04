package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.dto.EmployeeRequestDTO;
import com.perfume.rasa.dto.EmployeeResponseDTO;
import com.perfume.rasa.model.User;
import com.perfume.rasa.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/employees")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
public class AdminEmployeeController {

    private final EmployeeService employeeService;

    public AdminEmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) User.Role role,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortOrder
    ) {
        try {
            Sort.Direction direction = "DESC".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            
            Page<EmployeeResponseDTO> employeesPage = employeeService.getFilteredEmployees(search, department, role, pageable);
            
            Map<String, Object> data = Map.of(
                    "content", employeesPage.getContent(),
                    "currentPage", employeesPage.getNumber(),
                    "totalItems", employeesPage.getTotalElements(),
                    "totalPages", employeesPage.getTotalPages()
            );
            
            return ResponseEntity.ok(new ApiResponse(true, "Employees retrieved successfully", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Failed to retrieve employees: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getEmployeeById(@PathVariable Long id) {
        try {
            EmployeeResponseDTO employee = employeeService.getEmployeeById(id);
            return ResponseEntity.ok(new ApiResponse(true, "Employee retrieved successfully", employee));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Employee not found: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse> createEmployee(@Valid @RequestBody EmployeeRequestDTO request) {
        try {
            EmployeeResponseDTO response = employeeService.createEmployee(request);
            return ResponseEntity.ok(new ApiResponse(true, "Employee created successfully", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "Failed to create employee: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequestDTO request) {
        try {
            EmployeeResponseDTO response = employeeService.updateEmployee(id, request);
            return ResponseEntity.ok(new ApiResponse(true, "Employee updated successfully", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "Failed to update employee: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteEmployee(@PathVariable Long id) {
        try {
            employeeService.deleteEmployee(id);
            return ResponseEntity.ok(new ApiResponse(true, "Employee deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PutMapping("/{id}/lock")
    public ResponseEntity<ApiResponse> toggleLockStatus(@PathVariable Long id) {
        try {
            employeeService.toggleLockStatus(id);
            return ResponseEntity.ok(new ApiResponse(true, "Employee account lock status updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PutMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> requestBody) {
        try {
            String newPassword = requestBody.get("newPassword");
            employeeService.resetPassword(id, newPassword);
            return ResponseEntity.ok(new ApiResponse(true, "Employee password reset successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "Failed to reset password: " + e.getMessage()));
        }
    }
}
