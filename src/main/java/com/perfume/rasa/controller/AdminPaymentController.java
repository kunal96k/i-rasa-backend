package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.dto.OrderResponseDTO;
import com.perfume.rasa.model.Order;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.OrderRepository;
import com.perfume.rasa.repository.UserRepository;
import com.perfume.rasa.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    private boolean isAdminOrEmployee(Authentication authentication) {
        if (authentication == null) return false;
        Optional<User> userOpt = userRepository.findByEmail(authentication.getName());
        if (userOpt.isPresent()) {
            User.Role role = userOpt.get().getRole();
            return role == User.Role.ADMIN || role == User.Role.EMPLOYEE || role == User.Role.SUPERADMIN;
        }
        return false;
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL") String paymentMethod,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            Authentication authentication) {

        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }

        try {
            String normalizedSortBy = "createdAt";
            if (Arrays.asList("id", "createdAt", "total", "transactionId", "paymentMethod", "status").contains(sortBy)) {
                normalizedSortBy = sortBy;
            }
            Sort.Direction direction = "ASC".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, normalizedSortBy));

            Specification<Order> spec = Specification.where(null);

            // Filters
            if (paymentMethod != null && !paymentMethod.trim().isEmpty() && !"ALL".equalsIgnoreCase(paymentMethod)) {
                String pm = paymentMethod.trim();
                spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("paymentMethod")), pm.toLowerCase()));
            }

            if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
                String st = status.trim().toUpperCase();
                spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), st));
            }

            // Search
            if (search != null && !search.trim().isEmpty()) {
                String q = "%" + search.trim().toLowerCase() + "%";
                
                Specification<Order> searchSpec = (root, query, cb) -> {
                    var billingJoin = root.join("billingAddress", jakarta.persistence.criteria.JoinType.LEFT);
                    var userJoin = root.join("user", jakarta.persistence.criteria.JoinType.LEFT);
                    
                    var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
                    predicates.add(cb.like(cb.lower(cb.coalesce(root.get("transactionId"), "")), q));
                    predicates.add(cb.like(cb.lower(cb.coalesce(billingJoin.get("fullName"), "")), q));
                    predicates.add(cb.like(cb.lower(cb.coalesce(billingJoin.get("email"), "")), q));
                    predicates.add(cb.like(cb.lower(cb.coalesce(userJoin.get("email"), "")), q));
                    
                    try {
                        Long idVal = Long.parseLong(search.trim());
                        predicates.add(cb.equal(root.get("id"), idVal));
                    } catch (NumberFormatException e) {
                        // ignore
                    }

                    return cb.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                };
                
                spec = spec.and(searchSpec);
            }

            // Date Range
            if (fromDate != null && !fromDate.trim().isEmpty()) {
                LocalDate start = LocalDate.parse(fromDate.trim());
                spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), LocalDateTime.of(start, LocalTime.MIN)));
            }
            if (toDate != null && !toDate.trim().isEmpty()) {
                LocalDate end = LocalDate.parse(toDate.trim());
                spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), LocalDateTime.of(end, LocalTime.MAX)));
            }

            Page<Order> ordersPage = orderRepository.findAll(spec, pageable);
            Page<OrderResponseDTO> dtoPage = ordersPage.map(orderService::mapToResponseDTO);

            Map<String, Object> data = Map.of(
                    "content", dtoPage.getContent(),
                    "currentPage", dtoPage.getNumber(),
                    "totalItems", dtoPage.getTotalElements(),
                    "totalPages", dtoPage.getTotalPages(),
                    "size", dtoPage.getSize(),
                    "sortBy", normalizedSortBy,
                    "sortOrder", direction.name()
            );

            return ResponseEntity.ok(new ApiResponse(true, "Payments retrieved successfully", data));
        } catch (Exception e) {
            log.error("Error retrieving admin payments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error retrieving payments: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<ApiResponse> updatePaymentInfo(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }

        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Order not found", null));
            }

            Order order = orderOpt.get();
            String transactionId = body.get("transactionId");
            String status = body.get("status");

            if (transactionId != null) {
                order.setTransactionId(transactionId.trim());
            }

            orderRepository.save(order);

            if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase(order.getStatus())) {
                orderService.updateOrderStatusAdmin(orderId, status.trim().toUpperCase());
            }

            Order updatedOrder = orderRepository.findById(orderId).orElse(order);
            OrderResponseDTO responseDTO = orderService.mapToResponseDTO(updatedOrder);

            return ResponseEntity.ok(new ApiResponse(true, "Payment information updated successfully", responseDTO));
        } catch (Exception e) {
            log.error("Error updating payment information for order {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error updating payment information: " + e.getMessage(), null));
        }
    }
}
