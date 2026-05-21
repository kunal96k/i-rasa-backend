package com.perfume.rasa.dto;

import java.math.BigDecimal;
import java.util.List;

public class OrderRequestDTO {
    private List<OrderItemRequestDTO> items;
    private BillingRequestDTO billing;
    private String paymentMethod;
    private String paymentProofUrl;
    private String transactionId;
    private String couponCode;
    private BigDecimal discount;
    private BigDecimal shipping;
    private BigDecimal total;

    public List<OrderItemRequestDTO> getItems() { return items; }
    public void setItems(List<OrderItemRequestDTO> items) { this.items = items; }
    public BillingRequestDTO getBilling() { return billing; }
    public void setBilling(BillingRequestDTO billing) { this.billing = billing; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentProofUrl() { return paymentProofUrl; }
    public void setPaymentProofUrl(String paymentProofUrl) { this.paymentProofUrl = paymentProofUrl; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getShipping() { return shipping; }
    public void setShipping(BigDecimal shipping) { this.shipping = shipping; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
}
