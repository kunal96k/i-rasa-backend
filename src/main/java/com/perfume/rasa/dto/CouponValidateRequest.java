package com.perfume.rasa.dto;

import java.math.BigDecimal;
import java.util.List;

public class CouponValidateRequest {
    private String code;
    private BigDecimal cartTotal;
    private List<OrderItemRequestDTO> items;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public BigDecimal getCartTotal() { return cartTotal; }
    public void setCartTotal(BigDecimal cartTotal) { this.cartTotal = cartTotal; }
    public List<OrderItemRequestDTO> getItems() { return items; }
    public void setItems(List<OrderItemRequestDTO> items) { this.items = items; }
}
