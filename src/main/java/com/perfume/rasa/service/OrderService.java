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

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository, EmailService emailService) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
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
            order.setBillingAddress(billing);

            // In checkout, billing is also shipping
            Address shipping = new Address();
            shipping.setUser(user);
            shipping.setLabel("Shipping");
            shipping.setFullName(billing.getFullName());
            shipping.setAddressLine1(billing.getAddressLine1());
            shipping.setAreaLocality(billing.getAreaLocality());
            shipping.setCity(billing.getCity());
            shipping.setPincode(billing.getPincode());
            shipping.setDefault(false);
            order.setShippingAddress(shipping);
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
                order.addItem(item);

                BigDecimal itemTotal = itemDTO.getPrice().multiply(BigDecimal.valueOf(itemDTO.getQty()));
                subtotal = subtotal.add(itemTotal);
            }
        }
        order.setSubtotal(subtotal);

        BigDecimal total = request.getTotal();
        if (total == null) {
            total = subtotal.subtract(order.getDiscount()).add(order.getShipping());
        }
        order.setTotal(total);
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        
        // Expected delivery date: e.g. 5 days from now
        order.setExpectedDeliveryDate(LocalDate.now().plusDays(5));

        Order savedOrder = orderRepository.save(order);

        // Send order received email
        if (user != null && user.getEmail() != null) {
            String fullName = user.getFullName() != null ? user.getFullName() : 
                    (order.getBillingAddress() != null ? order.getBillingAddress().getFullName() : "Customer");
            String deliveryAddressStr = order.getBillingAddress() != null ? 
                    (order.getBillingAddress().getAddressLine1() + ", " + order.getBillingAddress().getAreaLocality()) : "";
            String city = order.getBillingAddress() != null ? order.getBillingAddress().getCity() : "";

            emailService.sendOrderReceivedEmail(
                    user.getEmail(),
                    fullName,
                    savedOrder.getId(),
                    request.getItems(),
                    savedOrder.getSubtotal(),
                    savedOrder.getDiscount(),
                    savedOrder.getShipping(),
                    savedOrder.getTotal(),
                    savedOrder.getPaymentMethod(),
                    deliveryAddressStr,
                    city,
                    savedOrder.getExpectedDeliveryDate()
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

        if (!order.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN) {
            throw new RuntimeException("Unauthorized access to order");
        }

        return mapToResponseDTO(order);
    }

    private OrderResponseDTO mapToResponseDTO(Order order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setOrderId(order.getId());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setStatus(order.getStatus());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setCouponCode(order.getCouponCode());
        dto.setDiscount(order.getDiscount());
        dto.setShipping(order.getShipping());
        dto.setSubtotal(order.getSubtotal());
        dto.setTotal(order.getTotal());
        dto.setExpectedDeliveryDate(order.getExpectedDeliveryDate());
        if (order.getUser() != null) {
            dto.setUserEmail(order.getUser().getEmail());
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
            if (order.getUser() != null) {
                billingDTO.setPhone(order.getUser().getPhone());
                billingDTO.setEmail(order.getUser().getEmail());
            }
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
            if (order.getUser() != null) {
                shippingDTO.setPhone(order.getUser().getPhone());
                shippingDTO.setEmail(order.getUser().getEmail());
            }
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
                itemDTOs.add(itemDTO);
            }
            dto.setItems(itemDTOs);
        }

        return dto;
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
}