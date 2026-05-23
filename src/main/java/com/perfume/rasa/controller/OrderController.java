package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.dto.OrderRequestDTO;
import com.perfume.rasa.dto.OrderResponseDTO;
import com.perfume.rasa.model.Order;
import com.perfume.rasa.repository.OrderRepository;
import com.perfume.rasa.repository.UserRepository;
import com.perfume.rasa.model.User;
import com.perfume.rasa.service.InvoiceService;
import com.perfume.rasa.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

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
            // Simple approach: read token param from request
            String accessToken = null;
            HttpServletRequest request = ((org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest();
            if (request != null) {
                accessToken = request.getParameter("token");
            }

            java.util.Optional<com.perfume.rasa.model.Order> orderOpt = orderRepository.findById(orderId);
            if (!orderOpt.isPresent()) {
                return ResponseEntity.status(404).body(new ApiResponse(false, "Order not found", null));
            }
            com.perfume.rasa.model.Order order = orderOpt.get();

            // 1. If token matches, allow access (both authenticated and guest orders)
            if (accessToken != null && accessToken.equals(order.getAccessToken())) {
                OrderResponseDTO dto = orderService.mapToResponseDTO(order);
                return ResponseEntity.ok(new ApiResponse(true, "Order retrieved successfully", dto));
            }

            // 2. Otherwise check if authenticated, and let OrderService check permission
            String principal = authentication != null ? authentication.getName() : null;
            if (principal != null) {
                OrderResponseDTO response = orderService.getOrderForUser(orderId, principal);
                return ResponseEntity.ok(new ApiResponse(true, "Order retrieved successfully", response));
            }

            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage(), null));
        }
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam("status") String status,
            Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized", null));
            }
            OrderResponseDTO response = orderService.updateOrderStatus(orderId, status.trim().toUpperCase(), authentication.getName());
            return ResponseEntity.ok(new ApiResponse(true, "Order status updated successfully", response));
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

    /**
     * Get invoice data for a specific order (for viewing in modal)
     */
    @GetMapping("/{orderId}/invoice")
    public ResponseEntity<ApiResponse> getInvoice(
            @PathVariable Long orderId,
            @RequestParam(value = "token", required = false) String token,
            Authentication authentication) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (!orderOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderOpt.get();
            
            boolean authorized = false;
            // 1. Authorized if token matches
            if (token != null && token.equals(order.getAccessToken())) {
                authorized = true;
            }
            // 2. Authorized if user is logged in and owns the order, or is admin
            else if (authentication != null) {
                String username = authentication.getName();
                Optional<User> currentUserOpt = userRepository.findByEmail(username);
                if (currentUserOpt.isPresent()) {
                    User currentUser = currentUserOpt.get();
                    if (currentUser.getRole() == User.Role.ADMIN) {
                        authorized = true;
                    } else if (order.getUser() != null && order.getUser().getId().equals(currentUser.getId())) {
                        authorized = true;
                    }
                }
            }

            if (!authorized) {
                return ResponseEntity.status(403).body(new ApiResponse(false, "Access denied", null));
            }

            Map<String, Object> invoiceSummary = invoiceService.getInvoiceSummary(order);
            log.info("Invoice data retrieved for order: {}", orderId);
            
            return ResponseEntity.ok(new ApiResponse(true, "Invoice retrieved successfully", invoiceSummary));
        } catch (Exception e) {
            log.error("Error retrieving invoice for order: {}", orderId, e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Failed to retrieve invoice", null));
        }
    }

    /**
     * Download invoice as PDF
     */
    @GetMapping("/{orderId}/invoice/download")
    public ResponseEntity<?> downloadInvoicePDF(
            @PathVariable Long orderId,
            @RequestParam(value = "token", required = false) String token,
            Authentication authentication) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (!orderOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Order order = orderOpt.get();
            
            boolean authorized = false;
            // 1. Authorized if token matches
            if (token != null && token.equals(order.getAccessToken())) {
                authorized = true;
            }
            // 2. Authorized if user is logged in and owns the order, or is admin
            else if (authentication != null) {
                String username = authentication.getName();
                Optional<User> currentUserOpt = userRepository.findByEmail(username);
                if (currentUserOpt.isPresent()) {
                    User currentUser = currentUserOpt.get();
                    if (currentUser.getRole() == User.Role.ADMIN) {
                        authorized = true;
                    } else if (order.getUser() != null && order.getUser().getId().equals(currentUser.getId())) {
                        authorized = true;
                    }
                }
            }

            if (!authorized) {
                return ResponseEntity.status(403).body(new ApiResponse(false, "Access denied", null));
            }

            ByteArrayOutputStream pdfStream = invoiceService.generateInvoicePDF(order);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                "invoice_" + order.getOrderId() + ".pdf");
            headers.setContentLength(pdfStream.size());

            log.info("Invoice PDF downloaded for order: {}", orderId);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfStream.toByteArray());

        } catch (Exception e) {
            log.error("Error generating PDF invoice for order: {}", orderId, e);
            return ResponseEntity.status(500).body(new ApiResponse(false,
                "Failed to generate invoice PDF", null));
        }
    }

    /**
     * Customer cancels their own PENDING or CONFIRMED order.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse> cancelOrder(
            @PathVariable Long orderId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized", null));
            }
            String reason = body != null ? body.getOrDefault("reason", "") : "";
            OrderResponseDTO response = orderService.cancelOrder(orderId, reason, authentication.getName());
            return ResponseEntity.ok(new ApiResponse(true, "Order cancelled successfully", response));
        } catch (Exception e) {
            log.error("Error cancelling order {}: {}", orderId, e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage(), null));
        }
    }

    /**
     * Customer requests a refund on a DELIVERED order.
     */
    @PostMapping("/{orderId}/refund-request")
    public ResponseEntity<ApiResponse> requestRefund(
            @PathVariable Long orderId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized", null));
            }
            String reason = body != null ? body.getOrDefault("reason", "") : "";
            OrderResponseDTO response = orderService.requestRefund(orderId, reason, authentication.getName());
            return ResponseEntity.ok(new ApiResponse(true, "Refund request submitted successfully", response));
        } catch (Exception e) {
            log.error("Error requesting refund for order {}: {}", orderId, e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage(), null));
        }
    }

    /**
     * Customer requests an exchange on a DELIVERED order.
     */
    @PostMapping("/{orderId}/exchange-request")
    public ResponseEntity<ApiResponse> requestExchange(
            @PathVariable Long orderId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized", null));
            }
            String reason = body != null ? body.getOrDefault("reason", "") : "";
            OrderResponseDTO response = orderService.requestExchange(orderId, reason, authentication.getName());
            return ResponseEntity.ok(new ApiResponse(true, "Exchange request submitted successfully", response));
        } catch (Exception e) {
            log.error("Error requesting exchange for order {}: {}", orderId, e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage(), null));
        }
    }
}
