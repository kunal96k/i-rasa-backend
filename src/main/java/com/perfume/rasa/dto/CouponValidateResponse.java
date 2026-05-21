package com.perfume.rasa.dto;

import java.math.BigDecimal;

public class CouponValidateResponse {
    private BigDecimal discountAmount;

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
}
