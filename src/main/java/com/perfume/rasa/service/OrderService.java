package com.perfume.rasa.service;

import com.perfume.rasa.dto.BillingRequestDTO;
import com.perfume.rasa.dto.OrderItemRequestDTO;
import com.perfume.rasa.dto.OrderRequestDTO;
import com.perfume.rasa.dto.OrderResponseDTO;
import com.perfume.rasa.model.Address;
import com.perfume.rasa.model.Order;
import com.perfume.rasa.model.OrderItem;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.OrderRepository;
import com.perfume.rasa.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final InvoiceService invoiceService;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository, EmailService emailService, InvoiceService invoiceService) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.invoiceService = invoiceService;
    }

    @Transactional
    public OrderResponseDTO createOrder(OrderRequestDTO request, String username) {
        User user = null;
        if (username != null) {
            user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));
        }

        Order order = new Order();
        order.setUser(user);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setPaymentProofUrl(request.getPaymentProofUrl());
        order.setTransactionId(request.getTransactionId());
        order.setCouponCode(request.getCouponCode());
        order.setDiscount(request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO);
        order.setShipping(request.getShipping() != null ? request.getShipping() : BigDecimal.ZERO);
        order.setHandlingCharge(request.getHandlingCharge() != null ? request.getHandlingCharge() : BigDecimal.ZERO);
        order.setPlatformFee(request.getPlatformFee() != null ? request.getPlatformFee() : BigDecimal.ZERO);
        order.setPlatformServicesFee(request.getPlatformServicesFee() != null ? request.getPlatformServicesFee() : BigDecimal.ZERO);

        // Map billing and shipping address
        if (request.getBilling() != null) {
            BillingRequestDTO billingDTO = request.getBilling();
            
            Address billing = new Address();
            billing.setUser(user);
            billing.setLabel("Billing");
            billing.setFullName(billingDTO.getFirstName() + " " + billingDTO.getLastName());
            billing.setAddressLine1(billingDTO.getAddress1());
            billing.setAreaLocality(billingDTO.getAddress2() != null ? billingDTO.getAddress2() : "");
            billing.setCity(billingDTO.getCity());
            billing.setPincode(billingDTO.getZip());
            billing.setDefault(false);
            billing.setTemporaryOrderAddress(true);
            billing.setEmail(billingDTO.getEmail());
            billing.setPhone(billingDTO.getPhone());
            order.setBillingAddress(billing);
 
            BillingRequestDTO shippingDTO = request.getShippingAddress();
            if (shippingDTO != null) {
                Address shipping = new Address();
                shipping.setUser(user);
                shipping.setLabel("Shipping");
                String shippingFirst = shippingDTO.getFirstName() != null ? shippingDTO.getFirstName() : "";
                String shippingLast = shippingDTO.getLastName() != null ? shippingDTO.getLastName() : "";
                shipping.setFullName((shippingFirst + " " + shippingLast).trim());
                shipping.setAddressLine1(shippingDTO.getAddress1());
                shipping.setAreaLocality(shippingDTO.getAddress2() != null ? shippingDTO.getAddress2() : billing.getAreaLocality());
                shipping.setCity(shippingDTO.getCity());
                shipping.setPincode(shippingDTO.getZip());
                shipping.setDefault(false);
                shipping.setTemporaryOrderAddress(true);
                shipping.setEmail(shippingDTO.getEmail() != null ? shippingDTO.getEmail() : billing.getEmail());
                shipping.setPhone(shippingDTO.getPhone() != null ? shippingDTO.getPhone() : billing.getPhone());
                order.setShippingAddress(shipping);
            } else {
                Address shipping = new Address();
                shipping.setUser(user);
                shipping.setLabel("Shipping");
                shipping.setFullName(billing.getFullName());
                shipping.setAddressLine1(billing.getAddressLine1());
                shipping.setAreaLocality(billing.getAreaLocality());
                shipping.setCity(billing.getCity());
                shipping.setPincode(billing.getPincode());
                shipping.setDefault(false);
                shipping.setTemporaryOrderAddress(true);
                shipping.setEmail(billing.getEmail());
                shipping.setPhone(billing.getPhone());
                order.setShippingAddress(shipping);
            }
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        if (request.getItems() != null) {
            for (OrderItemRequestDTO itemDTO : request.getItems()) {
                OrderItem item = new OrderItem();
                item.setProductId(itemDTO.getProductId());
                item.setName(itemDTO.getName());
                item.setPrice(itemDTO.getPrice());
                item.setQty(itemDTO.getQty());
                item.setSize(itemDTO.getSize());
                item.setBottleType(itemDTO.getBottleType());
                item.setBottlePrice(itemDTO.getBottlePrice());
                order.addItem(item);

                BigDecimal bottleCost = itemDTO.getBottlePrice() != null ? itemDTO.getBottlePrice() : BigDecimal.ZERO;
                BigDecimal itemTotal = itemDTO.getPrice().add(bottleCost).multiply(BigDecimal.valueOf(itemDTO.getQty()));
                subtotal = subtotal.add(itemTotal);
            }
        }
        order.setSubtotal(subtotal);

        BigDecimal total = request.getTotal();
        if (total == null) {
            total = subtotal.subtract(order.getDiscount())
                    .add(order.getShipping())
                    .add(order.getHandlingCharge())
                    .add(order.getPlatformFee());
        }
        order.setTotal(total);
        if ("COD".equalsIgnoreCase(request.getPaymentMethod())) {
            order.setStatus("CONFIRMED");
        } else {
            order.setStatus("PENDING");
        }
        order.setCreatedAt(LocalDateTime.now());
        
        // Expected delivery date based on shipping location
        String shippingCity = order.getShippingAddress() != null ? order.getShippingAddress().getCity()
                : order.getBillingAddress() != null ? order.getBillingAddress().getCity() : "";
        int deliveryDays = 8; // Default: 8 days for outside Nashik
        if (shippingCity != null && shippingCity.toLowerCase().contains("nashik")) {
            deliveryDays = 3; // 3 days for Nashik
        }
        order.setExpectedDeliveryDate(LocalDate.now().plusDays(deliveryDays));

        // If guest order (no user) generate an access token so the guest can view the order
        if (user == null) {
            String token = UUID.randomUUID().toString();
            order.setAccessToken(token);
        }

        Order savedOrder = orderRepository.save(order);

        // Send order received email immediately after placement
        String recipientEmail = null;
        if (user != null && user.getEmail() != null) {
            recipientEmail = user.getEmail();
        } else if (order.getBillingAddress() != null && order.getBillingAddress().getEmail() != null) {
            recipientEmail = order.getBillingAddress().getEmail().trim();
        }

        if (recipientEmail != null && !recipientEmail.isEmpty()) {
            String fullName = "Customer";
            if (user != null && user.getFullName() != null && !user.getFullName().trim().isEmpty()) {
                fullName = user.getFullName().trim();
            } else if (order.getBillingAddress() != null && order.getBillingAddress().getFullName() != null && !order.getBillingAddress().getFullName().trim().isEmpty()) {
                fullName = order.getBillingAddress().getFullName().trim();
            }
            String deliveryAddressStr = buildDeliveryAddress(order);
            String city = order.getShippingAddress() != null ? order.getShippingAddress().getCity()
                    : order.getBillingAddress() != null ? order.getBillingAddress().getCity() : "";

            byte[] pdfBytes = null;
            byte[] guidelinesPdfBytes = null;
            try {
                java.io.ByteArrayOutputStream pdfStream = invoiceService.generateInvoicePDF(savedOrder);
                pdfBytes = pdfStream.toByteArray();
                
                java.io.ByteArrayOutputStream guidelinesStream = invoiceService.generateGuidelinesPDF();
                guidelinesPdfBytes = guidelinesStream.toByteArray();
            } catch (Exception e) {
                log.error("Failed to generate PDF invoice or guidelines for order placement email: {}", e.getMessage());
            }

            emailService.sendOrderReceivedEmail(
                    recipientEmail,
                    fullName,
                    savedOrder.getId(),
                    request.getItems(),
                    savedOrder.getSubtotal(),
                    savedOrder.getDiscount(),
                    savedOrder.getShipping(),
                    savedOrder.getHandlingCharge(),
                    savedOrder.getPlatformFee(),
                    savedOrder.getPlatformServicesFee(),
                    savedOrder.getTotal(),
                    savedOrder.getPaymentMethod(),
                    deliveryAddressStr,
                    city,
                    savedOrder.getExpectedDeliveryDate(),
                    pdfBytes,
                    guidelinesPdfBytes
            );
        }

        log.info("New order created with ID: {} for user: {}", savedOrder.getId(), username);
        return mapToResponseDTO(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getOrdersForUser(String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<OrderResponseDTO> response = new ArrayList<>();
        for (Order order : orders) {
            response.add(mapToResponseDTO(order));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderForUser(Long orderId, String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        if (order.getUser() == null) {
            if (user.getRole() != User.Role.ADMIN) {
                throw new RuntimeException("Unauthorized access to order");
            }
        } else if (!order.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN) {
            throw new RuntimeException("Unauthorized access to order");
        }

        return mapToResponseDTO(order);
    }

    public OrderResponseDTO mapToResponseDTO(Order order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setOrderId(order.getId());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setStatus(order.getStatus());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setCouponCode(order.getCouponCode());
        dto.setDiscount(order.getDiscount());
        dto.setShipping(order.getShipping());
        dto.setHandlingCharge(order.getHandlingCharge());
        dto.setPlatformFee(order.getPlatformFee());
        dto.setPlatformServicesFee(order.getPlatformServicesFee());
        dto.setSubtotal(order.getSubtotal());
        dto.setTotal(order.getTotal());
        dto.setExpectedDeliveryDate(order.getExpectedDeliveryDate());
        if (order.getUser() != null) {
            dto.setUserEmail(order.getUser().getEmail());
        } else if (order.getBillingAddress() != null) {
            dto.setUserEmail(order.getBillingAddress().getEmail());
        }

        if (order.getBillingAddress() != null) {
            BillingRequestDTO billingDTO = new BillingRequestDTO();
            String fullName = order.getBillingAddress().getFullName();
            String firstName = "";
            String lastName = "";
            if (fullName != null) {
                String[] parts = fullName.split(" ", 2);
                firstName = parts[0];
                if (parts.length > 1) {
                    lastName = parts[1];
                }
            }
            billingDTO.setFirstName(firstName);
            billingDTO.setLastName(lastName);
            billingDTO.setAddress1(order.getBillingAddress().getAddressLine1());
            billingDTO.setAddress2(order.getBillingAddress().getAreaLocality());
            billingDTO.setCity(order.getBillingAddress().getCity());
            billingDTO.setZip(order.getBillingAddress().getPincode());
            billingDTO.setPhone(order.getBillingAddress().getPhone() != null ? order.getBillingAddress().getPhone() : (order.getUser() != null ? order.getUser().getPhone() : null));
            billingDTO.setEmail(order.getBillingAddress().getEmail() != null ? order.getBillingAddress().getEmail() : (order.getUser() != null ? order.getUser().getEmail() : null));
            dto.setBilling(billingDTO);
        }

        if (order.getShippingAddress() != null) {
            BillingRequestDTO shippingDTO = new BillingRequestDTO();
            String fullName = order.getShippingAddress().getFullName();
            String firstName = "";
            String lastName = "";
            if (fullName != null) {
                String[] parts = fullName.split(" ", 2);
                firstName = parts[0];
                if (parts.length > 1) {
                    lastName = parts[1];
                }
            }
            shippingDTO.setFirstName(firstName);
            shippingDTO.setLastName(lastName);
            shippingDTO.setAddress1(order.getShippingAddress().getAddressLine1());
            shippingDTO.setAddress2(order.getShippingAddress().getAreaLocality());
            shippingDTO.setCity(order.getShippingAddress().getCity());
            shippingDTO.setZip(order.getShippingAddress().getPincode());
            shippingDTO.setPhone(order.getShippingAddress().getPhone() != null ? order.getShippingAddress().getPhone() : (order.getUser() != null ? order.getUser().getPhone() : null));
            shippingDTO.setEmail(order.getShippingAddress().getEmail() != null ? order.getShippingAddress().getEmail() : (order.getUser() != null ? order.getUser().getEmail() : null));
            dto.setShippingAddress(shippingDTO);
        }

        if (order.getItems() != null) {
            List<OrderItemRequestDTO> itemDTOs = new ArrayList<>();
            for (OrderItem item : order.getItems()) {
                OrderItemRequestDTO itemDTO = new OrderItemRequestDTO();
                itemDTO.setProductId(item.getProductId());
                itemDTO.setName(item.getName());
                itemDTO.setPrice(item.getPrice());
                itemDTO.setQty(item.getQty());
                itemDTO.setSize(item.getSize());
                itemDTO.setBottleType(item.getBottleType());
                itemDTO.setBottlePrice(item.getBottlePrice());
                itemDTOs.add(itemDTO);
            }
            dto.setItems(itemDTOs);
        }
        dto.setAccessToken(order.getAccessToken());

        return dto;
    }

    @Transactional
    public OrderResponseDTO updateOrderStatus(Long orderId, String status, String username) {
        User user = username != null ? userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username)) : null;

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        if (user != null) {
            if (order.getUser() == null) {
                if (user.getRole() != User.Role.ADMIN) {
                    throw new RuntimeException("Unauthorized to update order status");
                }
            } else if (!order.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN) {
                throw new RuntimeException("Unauthorized to update order status");
            }
        }

        order.setStatus(status);
        if (isFinalStatus(status)) {
            removeTemporaryOrderAddresses(order);
        }

        Order savedOrder = orderRepository.save(order);
        sendOrderStatusEmail(savedOrder);
        return mapToResponseDTO(savedOrder);
    }

    @Transactional
    public OrderResponseDTO updateOrderStatusAdmin(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        order.setStatus(status);
        if (isFinalStatus(status)) {
            removeTemporaryOrderAddresses(order);
        }

        Order savedOrder = orderRepository.save(order);
        sendOrderStatusEmail(savedOrder);
        return mapToResponseDTO(savedOrder);
    }

    private void sendOrderStatusEmail(Order order) {
        String recipientEmail = null;
        if (order.getUser() != null && order.getUser().getEmail() != null) {
            recipientEmail = order.getUser().getEmail();
        } else if (order.getBillingAddress() != null && order.getBillingAddress().getEmail() != null) {
            recipientEmail = order.getBillingAddress().getEmail().trim();
        }

        if (recipientEmail == null || recipientEmail.isEmpty()) {
            return;
        }

        String fullName = "Customer";
        if (order.getUser() != null && order.getUser().getFullName() != null && !order.getUser().getFullName().trim().isEmpty()) {
            fullName = order.getUser().getFullName().trim();
        } else if (order.getBillingAddress() != null && order.getBillingAddress().getFullName() != null && !order.getBillingAddress().getFullName().trim().isEmpty()) {
            fullName = order.getBillingAddress().getFullName().trim();
        }

        String deliveryAddress = buildDeliveryAddress(order);
        String city = order.getShippingAddress() != null ? order.getShippingAddress().getCity()
                : order.getBillingAddress() != null ? order.getBillingAddress().getCity() : "";
        java.util.List<OrderItemRequestDTO> itemDTOs = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                OrderItemRequestDTO itemDTO = new OrderItemRequestDTO();
                itemDTO.setProductId(item.getProductId());
                itemDTO.setName(item.getName());
                itemDTO.setPrice(item.getPrice());
                itemDTO.setQty(item.getQty());
                itemDTO.setSize(item.getSize());
                itemDTO.setBottleType(item.getBottleType());
                itemDTO.setBottlePrice(item.getBottlePrice());
                itemDTOs.add(itemDTO);
            }
        }

        byte[] pdfBytes = null;
        byte[] guidelinesPdfBytes = null;
        try {
            java.io.ByteArrayOutputStream pdfStream = invoiceService.generateInvoicePDF(order);
            pdfBytes = pdfStream.toByteArray();
            
            java.io.ByteArrayOutputStream guidelinesStream = invoiceService.generateGuidelinesPDF();
            guidelinesPdfBytes = guidelinesStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF invoice or guidelines for order status update email: {}", e.getMessage());
        }

        switch (order.getStatus() != null ? order.getStatus().toUpperCase() : "") {
            case "CONFIRMED":
                emailService.sendOrderConfirmedEmail(
                        recipientEmail,
                        fullName,
                        order.getId(),
                        itemDTOs,
                        order.getSubtotal(),
                        order.getDiscount(),
                        order.getShipping(),
                        order.getHandlingCharge(),
                        order.getPlatformFee(),
                        order.getPlatformServicesFee(),
                        order.getTotal(),
                        order.getPaymentMethod(),
                        deliveryAddress,
                        city,
                        order.getExpectedDeliveryDate(),
                        pdfBytes,
                        guidelinesPdfBytes
                );
                break;
            case "DELIVERED":
                emailService.sendOrderDeliveredEmail(
                        recipientEmail,
                        fullName,
                        order.getId(),
                        itemDTOs,
                        order.getSubtotal(),
                        order.getDiscount(),
                        order.getShipping(),
                        order.getHandlingCharge(),
                        order.getPlatformFee(),
                        order.getPlatformServicesFee(),
                        order.getTotal(),
                        order.getPaymentMethod(),
                        deliveryAddress,
                        city,
                        order.getExpectedDeliveryDate(),
                        pdfBytes,
                        guidelinesPdfBytes
                );
                break;
            default:
                break;
        }
    }

    private String buildDeliveryAddress(Order order) {
        Address address = order.getShippingAddress() != null ? order.getShippingAddress() : order.getBillingAddress();
        if (address == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (address.getAddressLine1() != null) {
            sb.append(address.getAddressLine1());
        }
        if (address.getAreaLocality() != null && !address.getAreaLocality().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(address.getAreaLocality());
        }
        if (address.getCity() != null && !address.getCity().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(address.getCity());
        }
        if (address.getPincode() != null && !address.getPincode().isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(address.getPincode());
        }
        return sb.toString();
    }

    private boolean isFinalStatus(String status) {
        return status != null && (
                status.equalsIgnoreCase("COMPLETED") ||
                status.equalsIgnoreCase("DELIVERED") ||
                status.equalsIgnoreCase("CANCELLED") ||
                status.equalsIgnoreCase("REFUNDED")
        );
    }

    private void removeTemporaryOrderAddresses(Order order) {
        if (order.getBillingAddress() != null && order.getBillingAddress().isTemporaryOrderAddress()) {
            order.setBillingAddress(null);
        }
        if (order.getShippingAddress() != null && order.getShippingAddress().isTemporaryOrderAddress()) {
            order.setShippingAddress(null);
        }
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> getFilteredOrdersForUser(
            String username,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String search,
            Pageable pageable
    ) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Page<Order> ordersPage = orderRepository.findFilteredOrders(
                user.getId(),
                (status == null || status.trim().isEmpty()) ? null : status,
                startDate,
                endDate,
                (search == null || search.trim().isEmpty()) ? null : search,
                pageable
        );

        return ordersPage.map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> getAllOrdersForAdmin(
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String search,
            Pageable pageable
    ) {
        Page<Order> ordersPage = orderRepository.findAllFilteredOrders(
                (status == null || status.trim().isEmpty()) ? null : status,
                startDate,
                endDate,
                (search == null || search.trim().isEmpty()) ? null : search,
                pageable
        );

        return ordersPage.map(this::mapToResponseDTO);
    }
    /**
     * Cancel an order — customers can cancel PENDING or CONFIRMED orders.
     */
    @Transactional
    public OrderResponseDTO cancelOrder(Long orderId, String reason, String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Only order owner can cancel (or admin)
        if (order.getUser() == null || (!order.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN)) {
            throw new RuntimeException("Unauthorized: you don't own this order");
        }

        String currentStatus = order.getStatus() != null ? order.getStatus().toUpperCase() : "";
        if (!currentStatus.matches("(PENDING|CONFIRMED)")) {
            throw new RuntimeException("Order cannot be cancelled in status: " + order.getStatus() +
                ". Only PENDING or CONFIRMED orders can be cancelled.");
        }

        order.setStatus("CANCELLED");
        if (reason != null && !reason.trim().isEmpty()) {
            log.info("Order {} cancelled by user {} with reason: {}", orderId, username, reason);
        }
        Order saved = orderRepository.save(order);

        // Send cancellation email
        sendCancellationEmail(saved, reason);
        return mapToResponseDTO(saved);
    }

    /**
     * Request refund — customers can request refund on DELIVERED orders within policy window.
     */
    @Transactional
    public OrderResponseDTO requestRefund(Long orderId, String reason, String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getUser() == null || (!order.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN)) {
            throw new RuntimeException("Unauthorized: you don't own this order");
        }

        String currentStatus = order.getStatus() != null ? order.getStatus().toUpperCase() : "";
        if (!currentStatus.equals("DELIVERED")) {
            throw new RuntimeException("Refund can only be requested for DELIVERED orders. Current status: " + order.getStatus());
        }

        order.setStatus("REFUNDED");
        log.info("Refund requested for order {} by {} — reason: {}", orderId, username, reason);
        Order saved = orderRepository.save(order);

        sendRefundRequestEmail(saved, reason);
        return mapToResponseDTO(saved);
    }

    /**
     * Request exchange — customers can request exchange on DELIVERED orders.
     */
    @Transactional
    public OrderResponseDTO requestExchange(Long orderId, String reason, String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getUser() == null || (!order.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN)) {
            throw new RuntimeException("Unauthorized: you don't own this order");
        }

        String currentStatus = order.getStatus() != null ? order.getStatus().toUpperCase() : "";
        if (!currentStatus.equals("DELIVERED")) {
            throw new RuntimeException("Exchange can only be requested for DELIVERED orders. Current status: " + order.getStatus());
        }

        order.setStatus("EXCHANGED");
        log.info("Exchange requested for order {} by {} — reason: {}", orderId, username, reason);
        Order saved = orderRepository.save(order);

        sendExchangeRequestEmail(saved, reason);
        return mapToResponseDTO(saved);
    }

    private void sendCancellationEmail(Order order, String reason) {
        try {
            String recipientEmail = order.getUser() != null ? order.getUser().getEmail()
                    : (order.getBillingAddress() != null ? order.getBillingAddress().getEmail() : null);
            String fullName = order.getUser() != null ? order.getUser().getFullName() : "Customer";
            if (recipientEmail == null || recipientEmail.isEmpty()) return;

            emailService.sendSimpleEmail(recipientEmail,
                "Order #RASA-" + order.getId() + " Cancelled — I Rasa Perfumes",
                "Dear " + fullName + ",\n\n" +
                "Your order RASA-" + order.getId() + " has been successfully cancelled.\n" +
                (reason != null && !reason.isBlank() ? "Reason: " + reason + "\n" : "") +
                "\nIf you paid online, your refund will be processed within 5-7 business days.\n\n" +
                "Thank you for shopping with I Rasa Perfumes.\n\nWarm regards,\nI Rasa Perfumes Team");
        } catch (Exception e) {
            log.error("Failed to send cancellation email for order {}: {}", order.getId(), e.getMessage());
        }
    }

    private void sendRefundRequestEmail(Order order, String reason) {
        try {
            String recipientEmail = order.getUser() != null ? order.getUser().getEmail()
                    : (order.getBillingAddress() != null ? order.getBillingAddress().getEmail() : null);
            String fullName = order.getUser() != null ? order.getUser().getFullName() : "Customer";
            if (recipientEmail == null || recipientEmail.isEmpty()) return;

            emailService.sendSimpleEmail(recipientEmail,
                "Refund Request Received — Order RASA-" + order.getId(),
                "Dear " + fullName + ",\n\n" +
                "We've received your refund request for order RASA-" + order.getId() + ".\n" +
                (reason != null && !reason.isBlank() ? "Reason: " + reason + "\n" : "") +
                "\nOur team will review your request and process the refund within 5-7 business days.\n\n" +
                "For queries, contact us at support@rasaperfumes.in\n\nWarm regards,\nI Rasa Perfumes Team");
        } catch (Exception e) {
            log.error("Failed to send refund email for order {}: {}", order.getId(), e.getMessage());
        }
    }

    private void sendExchangeRequestEmail(Order order, String reason) {
        try {
            String recipientEmail = order.getUser() != null ? order.getUser().getEmail()
                    : (order.getBillingAddress() != null ? order.getBillingAddress().getEmail() : null);
            String fullName = order.getUser() != null ? order.getUser().getFullName() : "Customer";
            if (recipientEmail == null || recipientEmail.isEmpty()) return;

            emailService.sendSimpleEmail(recipientEmail,
                "Exchange Request Received — Order RASA-" + order.getId(),
                "Dear " + fullName + ",\n\n" +
                "We've received your exchange request for order RASA-" + order.getId() + ".\n" +
                (reason != null && !reason.isBlank() ? "Reason: " + reason + "\n" : "") +
                "\nOur team will get in touch with you within 24-48 hours to arrange the exchange.\n\n" +
                "For queries, contact us at support@rasaperfumes.in\n\nWarm regards,\nI Rasa Perfumes Team");
        } catch (Exception e) {
            log.error("Failed to send exchange email for order {}: {}", order.getId(), e.getMessage());
        }
    }
}