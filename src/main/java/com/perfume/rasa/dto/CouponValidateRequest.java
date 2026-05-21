package com.perfume.rasa.dto;

import java.math.BigDecimal;

public class CouponValidateRequest {
    private String code;
    private BigDecimal cartTotal;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public BigDecimal getCartTotal() { return cartTotal; }
    public void setCartTotal(BigDecimal cartTotal) { this.cartTotal = cartTotal; }
}
