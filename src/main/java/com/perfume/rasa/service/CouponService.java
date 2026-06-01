package com.perfume.rasa.service;

import com.perfume.rasa.dto.CouponValidateRequest;
import com.perfume.rasa.dto.CouponValidateResponse;
import com.perfume.rasa.model.Coupon;
import com.perfume.rasa.repository.CouponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class CouponService {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private com.perfume.rasa.repository.UserRepository userRepository;

    @Autowired
    private com.perfume.rasa.repository.OrderRepository orderRepository;

    public CouponValidateResponse validateCoupon(CouponValidateRequest request,
            org.springframework.security.core.Authentication authentication) {

        String code = request.getCode();
        if (code == null || code.trim().isEmpty()) {
            throw new RuntimeException("Invalid coupon code");
        }

        // Try case-insensitive lookup
        Coupon coupon = couponRepository.findByCode(code.trim().toUpperCase())
                .orElseGet(() -> couponRepository.findByCode(code.trim())
                        .orElseThrow(() -> new RuntimeException("Invalid coupon code")));

        if (!coupon.isActive()) {
            throw new RuntimeException("Coupon is not active");
        }

        if (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Coupon has expired");
        }

        if (coupon.getMinCartValue() != null
                && request.getCartTotal() != null
                && request.getCartTotal().compareTo(coupon.getMinCartValue()) < 0) {
            throw new RuntimeException("Minimum cart value of Rs. "
                    + coupon.getMinCartValue().toPlainString() + " required for this coupon");
        }

        // Calculate discount amount
        BigDecimal discount = BigDecimal.ZERO;
        if (coupon.getDiscountAmount() != null) {
            discount = coupon.getDiscountAmount();
        } else if (coupon.getDiscountPercentage() != null && request.getCartTotal() != null) {
            discount = request.getCartTotal()
                    .multiply(coupon.getDiscountPercentage())
                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        }

        CouponValidateResponse response = new CouponValidateResponse();
        response.setDiscountAmount(discount);
        return response;
    }

    public java.util.List<Coupon> getActiveCoupons() {
        return couponRepository.findAll().stream()
                .filter(c -> c.isActive()
                        && (c.getExpiryDate() == null || c.getExpiryDate().isAfter(LocalDateTime.now())))
                .collect(java.util.stream.Collectors.toList());
    }
}
