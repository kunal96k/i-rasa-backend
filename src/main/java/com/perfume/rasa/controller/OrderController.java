package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.dto.OrderRequestDTO;
import com.perfume.rasa.dto.OrderResponseDTO;
import com.perfume.rasa.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Value("${app.upload.storage-dir:upload}")
    private String uploadDir;

    @PostMapping
    public ResponseEntity<ApiResponse> createOrder(
            @RequestBody OrderRequestDTO request,
            Authentication authentication) {
        try {
            String username = authentication != null ? authentication.getName() : null;
            OrderResponseDTO response = orderService.createOrder(request, username);
            return ResponseEntity.ok(new ApiResponse(true, "Order placed successfully", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String search,
            Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized", null));
            }
            
            java.time.LocalDateTime start = null;
            if (startDate != null && !startDate.trim().isEmpty()) {
                start = java.time.LocalDate.parse(startDate).atStartOfDay();
            }
            java.time.LocalDateTime end = null;
            if (endDate != null && !endDate.trim().isEmpty()) {
                end = java.time.LocalDate.parse(endDate).atTime(23, 59, 59, 999999999);
            }

            String uppercaseStatus = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : null;

            org.springframework.data.domain.Pageable pageable = 
                    org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
            
            org.springframework.data.domain.Page<OrderResponseDTO> ordersPage = 
                    orderService.getFilteredOrdersForUser(authentication.getName(), uppercaseStatus, start, end, search, pageable);

            Map<String, Object> data = Map.of(
                "orders", ordersPage.getContent(),
                "currentPage", ordersPage.getNumber(),
                "totalItems", ordersPage.getTotalElements(),
                "totalPages", ordersPage.getTotalPages()
            );

            return ResponseEntity.ok(new ApiResponse(true, "Orders retrieved successfully", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse> getOrder(@PathVariable Long orderId, Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized", null));
            }
            OrderResponseDTO response = orderService.getOrderForUser(orderId, authentication.getName());
            return ResponseEntity.ok(new ApiResponse(true, "Order retrieved successfully", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/payment-proof")
    public ResponseEntity<ApiResponse> uploadPaymentProof(
            @RequestParam("file") MultipartFile file,
            @RequestParam("transactionId") String transactionId) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("File is empty");
            }

            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path path = Paths.get(uploadDir, filename);
            Files.write(path, file.getBytes());

            String fileUrl = "/upload/" + filename; // Adjust base URL logic if needed
            return ResponseEntity.ok(new ApiResponse(true, "Payment proof uploaded", Map.of("url", fileUrl)));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Upload failed: " + e.getMessage(), null));
        }
    }
}
