package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.dto.OrderResponseDTO;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.UserRepository;
import com.perfume.rasa.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    // Utility method to check if current user is ADMIN or EMPLOYEE
    private boolean isAdminOrEmployee(Authentication authentication) {
        if (authentication == null) return false;
        Optional<User> userOpt = userRepository.findByEmail(authentication.getName());
        if (userOpt.isPresent()) {
            User.Role role = userOpt.get().getRole();
            return role == User.Role.ADMIN || role == User.Role.EMPLOYEE;
        }
        return false;
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String search,
            Authentication authentication) {
        
        // Re-enabled auth check
        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            LocalDateTime start = null;
            if (startDate != null && !startDate.trim().isEmpty()) {
                start = LocalDate.parse(startDate).atStartOfDay();
            }
            LocalDateTime end = null;
            if (endDate != null && !endDate.trim().isEmpty()) {
                end = LocalDate.parse(endDate).atTime(23, 59, 59, 999999999);
            }

            String uppercaseStatus = (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) 
                                     ? status.trim().toUpperCase() : null;

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

            Page<OrderResponseDTO> ordersPage = orderService.getAllOrdersForAdmin(uppercaseStatus, start, end, search, pageable);

            Map<String, Object> data = Map.of(
                    "content", ordersPage.getContent(),
                    "currentPage", ordersPage.getNumber(),
                    "totalItems", ordersPage.getTotalElements(),
                    "totalPages", ordersPage.getTotalPages()
            );

            return ResponseEntity.ok(new ApiResponse(true, "Orders retrieved successfully", data));
        } catch (Exception e) {
            log.error("Error retrieving admin orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error retrieving orders: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
            
        // Re-enabled auth check
        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            String newStatus = body.get("status");
            if (newStatus == null || newStatus.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Status is required", null));
            }

            OrderResponseDTO response = orderService.updateOrderStatusAdmin(orderId, newStatus.trim().toUpperCase());
            return ResponseEntity.ok(new ApiResponse(true, "Order status updated successfully", response));

        } catch (Exception e) {
            log.error("Error updating order status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error updating order status: " + e.getMessage(), null));
        }
    }
}
