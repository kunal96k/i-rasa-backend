package com.perfume.rasa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class OrderResponseDTO {
    private Long orderId;
    private LocalDateTime createdAt;
    private String status;
    private String paymentMethod;
    private String couponCode;
    private BigDecimal discount;
    private BigDecimal shipping;
    private BigDecimal handlingCharge;
    private BigDecimal platformFee;
    private BigDecimal platformServicesFee;
    private BigDecimal subtotal;
    private BigDecimal total;
    private LocalDate expectedDeliveryDate;
    private String userEmail;
    private String accessToken;
    private BillingRequestDTO billing;
    private BillingRequestDTO shippingAddress;
    private List<OrderItemRequestDTO> items;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getShipping() { return shipping; }
    public void setShipping(BigDecimal shipping) { this.shipping = shipping; }
    public BigDecimal getHandlingCharge() { return handlingCharge; }
    public void setHandlingCharge(BigDecimal handlingCharge) { this.handlingCharge = handlingCharge; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public void setPlatformFee(BigDecimal platformFee) { this.platformFee = platformFee; }
    public BigDecimal getPlatformServicesFee() { return platformServicesFee; }
    public void setPlatformServicesFee(BigDecimal platformServicesFee) { this.platformServicesFee = platformServicesFee; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public BillingRequestDTO getBilling() { return billing; }
    public void setBilling(BillingRequestDTO billing) { this.billing = billing; }
    public BillingRequestDTO getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(BillingRequestDTO shippingAddress) { this.shippingAddress = shippingAddress; }
    public List<OrderItemRequestDTO> getItems() { return items; }
    public void setItems(List<OrderItemRequestDTO> items) { this.items = items; }
    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(LocalDate d) { this.expectedDeliveryDate = d; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
}
