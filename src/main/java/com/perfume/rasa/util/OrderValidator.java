package com.perfume.rasa.util;

import com.perfume.rasa.model.Order;
import com.perfume.rasa.model.OrderItem;
import com.perfume.rasa.exception.DataIntegrityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Validator for Order data consistency and integrity
 * Ensures all orders meet business logic requirements
 */
@Slf4j
@Component
public class OrderValidator {

    /**
     * Validate order data for consistency
     */
    public void validateOrderConsistency(Order order) {
        if (order == null) {
            throw new DataIntegrityException("Order", "Order object is null");
        }

        // Check required fields
        if (order.getUser() == null) {
            throw new DataIntegrityException("Order", "Order must have an associated user");
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new DataIntegrityException("Order", "Order must contain at least one item");
        }

        // Validate pricing
        validateOrderPricing(order);

        // Validate items
        validateOrderItems(order);

        // Validate addresses
        if (order.getBillingAddress() == null) {
            throw new DataIntegrityException("Order", "Billing address is required");
        }

        if (order.getShippingAddress() == null) {
            throw new DataIntegrityException("Order", "Shipping address is required");
        }

        log.info("Order validation passed for order: {}", order.getOrderId());
    }

    /**
     * Validate order pricing calculations
     */
    public void validateOrderPricing(Order order) {
        if (order.getSubtotal() == null || order.getSubtotal().signum() < 0) {
            throw new DataIntegrityException("Order", "Subtotal must be non-negative");
        }

        if (order.getDiscount() != null && order.getDiscount().signum() < 0) {
            throw new DataIntegrityException("Order", "Discount cannot be negative");
        }

        if (order.getShipping() != null && order.getShipping().signum() < 0) {
            throw new DataIntegrityException("Order", "Shipping cost cannot be negative");
        }

        if (order.getHandlingCharge() != null && order.getHandlingCharge().signum() < 0) {
            throw new DataIntegrityException("Order", "Handling charge cannot be negative");
        }

        if (order.getPlatformFee() != null && order.getPlatformFee().signum() < 0) {
            throw new DataIntegrityException("Order", "Platform fee cannot be negative");
        }

        // Calculate expected total
        BigDecimal calculatedTotal = calculateExpectedTotal(order);
        
        // Allow small rounding differences (0.01)
        BigDecimal difference = order.getTotal().subtract(calculatedTotal).abs();
        if (difference.compareTo(new BigDecimal("0.01")) > 0) {
            log.warn("Order {} has price mismatch. Reported: {}, Calculated: {}",
                order.getOrderId(), order.getTotal(), calculatedTotal);
            throw new DataIntegrityException("Order", 
                String.format("Price calculation mismatch. Total should be approximately %.2f but is %.2f",
                    calculatedTotal, order.getTotal()));
        }
    }

    /**
     * Calculate expected order total
     */
    private BigDecimal calculateExpectedTotal(Order order) {
        BigDecimal total = order.getSubtotal();
        
        if (order.getDiscount() != null) {
            total = total.subtract(order.getDiscount());
        }
        if (order.getShipping() != null) {
            total = total.add(order.getShipping());
        }
        if (order.getHandlingCharge() != null) {
            total = total.add(order.getHandlingCharge());
        }
        if (order.getPlatformFee() != null) {
            total = total.add(order.getPlatformFee());
        }
        
        return total;
    }

    /**
     * Validate order items
     */
    public void validateOrderItems(Order order) {
        Set<String> itemIds = new HashSet<>();
        BigDecimal itemsTotal = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            // Check for duplicate items
            if (!itemIds.add(item.getProductId())) {
                throw new DataIntegrityException("Order", 
                    "Duplicate product in order: " + item.getProductId());
            }

            // Validate item data
            if (item.getQty() == null || item.getQty() <= 0) {
                throw new DataIntegrityException("Order", "Item quantity must be positive");
            }

            if (item.getPrice() == null || item.getPrice().signum() < 0) {
                throw new DataIntegrityException("Order", "Item price cannot be negative");
            }

            // Validate quantity limits
            if (item.getQty() > 10000) {
                throw new DataIntegrityException("Order", 
                    "Item quantity exceeds maximum limit: " + item.getQty());
            }

            itemsTotal = itemsTotal.add(
                item.getPrice().multiply(new BigDecimal(item.getQty()))
            );
        }

        // Verify subtotal matches items total
        BigDecimal difference = order.getSubtotal().subtract(itemsTotal).abs();
        if (difference.compareTo(new BigDecimal("0.01")) > 0) {
            log.warn("Order {} has subtotal mismatch. Reported: {}, Calculated: {}",
                order.getOrderId(), order.getSubtotal(), itemsTotal);
            throw new DataIntegrityException("Order", 
                "Subtotal mismatch with calculated items total");
        }
    }

    /**
     * Validate order status transition
     */
    public void validateStatusTransition(String currentStatus, String newStatus) {
        if (!DataValidator.isValidOrderStatus(newStatus)) {
            throw new DataIntegrityException("Order", "Invalid order status: " + newStatus);
        }

        // Define valid status transitions
        switch (currentStatus.toUpperCase()) {
            case "PENDING":
                // From PENDING, can go to PROCESSING, CANCELLED, or CONFIRMED
                if (!newStatus.matches("(PROCESSING|CANCELLED|CONFIRMED)")) {
                    throw new DataIntegrityException("Order",
                        "Cannot transition from PENDING to " + newStatus);
                }
                break;
            case "PROCESSING":
                // From PROCESSING, can go to SHIPPED or CANCELLED
                if (!newStatus.matches("(SHIPPED|CANCELLED)")) {
                    throw new DataIntegrityException("Order",
                        "Cannot transition from PROCESSING to " + newStatus);
                }
                break;
            case "CONFIRMED":
                // From CONFIRMED, can go to PROCESSING or CANCELLED
                if (!newStatus.matches("(PROCESSING|CANCELLED)")) {
                    throw new DataIntegrityException("Order",
                        "Cannot transition from CONFIRMED to " + newStatus);
                }
                break;
            case "SHIPPED":
                // From SHIPPED, can only go to DELIVERED or CANCELLED
                if (!newStatus.matches("(DELIVERED|CANCELLED)")) {
                    throw new DataIntegrityException("Order",
                        "Cannot transition from SHIPPED to " + newStatus);
                }
                break;
            case "DELIVERED":
                // From DELIVERED, customers can request REFUNDED or EXCHANGED
                if (!newStatus.matches("(REFUNDED|EXCHANGED)")) {
                    throw new DataIntegrityException("Order",
                        "Cannot transition from DELIVERED to " + newStatus);
                }
                break;
            case "CANCELLED":
                // Terminal state, cannot transition
                throw new DataIntegrityException("Order",
                    "Cannot change status from terminal state: " + currentStatus);
            case "REFUNDED":
            case "EXCHANGED":
                // Terminal states, cannot transition
                throw new DataIntegrityException("Order",
                    "Cannot change status from terminal state: " + currentStatus);
            default:
                throw new DataIntegrityException("Order", "Unknown order status: " + currentStatus);
        }
    }

    /**
     * Validate coupon is applicable to order
     */
    public void validateCouponApplicability(Order order, String couponCode) {
        if (couponCode == null || couponCode.trim().isEmpty()) {
            return;
        }

        if (!DataValidator.isValidCouponCode(couponCode)) {
            throw new DataIntegrityException("Coupon", "Invalid coupon code format: " + couponCode);
        }

        // Additional coupon validation logic can be added here
        log.debug("Coupon {} applied to order {}", couponCode, order.getOrderId());
    }

    /**
     * Validate payment information
     */
    public void validatePaymentInfo(Order order) {
        if (order.getPaymentMethod() == null || order.getPaymentMethod().isEmpty()) {
            throw new DataIntegrityException("Payment", "Payment method is required");
        }

        if (!DataValidator.isValidPaymentMethod(order.getPaymentMethod())) {
            throw new DataIntegrityException("Payment", "Invalid payment method: " + order.getPaymentMethod());
        }

        // If payment is confirmed, transaction ID should be present
        if (order.getStatus() != null && 
            (order.getStatus().equals("CONFIRMED") || order.getStatus().equals("COMPLETED")) &&
            (order.getTransactionId() == null || order.getTransactionId().isEmpty())) {
            log.warn("Order {} in status {} but missing transaction ID", 
                order.getOrderId(), order.getStatus());
        }
    }
}
