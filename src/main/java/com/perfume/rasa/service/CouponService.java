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
        if (code != null && code.trim().equalsIgnoreCase("WELCOME20")) {
            // Check if user has already placed an order
            if (authentication != null && authentication.isAuthenticated()
                    && !authentication.getName().equals("anonymousUser")) {
                String email = authentication.getName();
                com.perfume.rasa.model.User user = userRepository.findByEmail(email).orElse(null);
                if (user != null) {
                    java.util.List<com.perfume.rasa.model.Order> orders = orderRepository
                            .findByUserIdOrderByCreatedAtDesc(user.getId());
                    if (orders != null && !orders.isEmpty()) {
                        throw new RuntimeException("Coupon WELCOME20 is only applicable for your first order.");
                    }
                }
            }

            // Seed WELCOME20 if not present
            if (!couponRepository.findByCode("WELCOME20").isPresent()) {
                Coupon welcomeCoupon = new Coupon();
                welcomeCoupon.setCode("WELCOME20");
                welcomeCoupon.setDiscountPercentage(new BigDecimal("20.00"));
                welcomeCoupon.setMinCartValue(new BigDecimal("0.00"));
                welcomeCoupon.setActive(true);
                welcomeCoupon.setExpiryDate(LocalDateTime.now().plusYears(10));
                couponRepository.save(welcomeCoupon);
            }
        }

        Coupon coupon = couponRepository.findByCode(request.getCode())
                .orElseThrow(() -> new RuntimeException("Invalid coupon code"));

        if (!coupon.isActive()) {
            throw new RuntimeException("Coupon is not active");
        }

        if (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Coupon has expired");
        }

        if (coupon.getMinCartValue() != null && request.getCartTotal().compareTo(coupon.getMinCartValue()) < 0) {
            throw new RuntimeException("Minimum cart value not met for this coupon");
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (coupon.getDiscountAmount() != null) {
            discount = coupon.getDiscountAmount();
        } else if (coupon.getDiscountPercentage() != null) {
            discount = request.getCartTotal().multiply(coupon.getDiscountPercentage()).divide(new BigDecimal("100"));
        }

        CouponValidateResponse response = new CouponValidateResponse();
        response.setDiscountAmount(discount);
        return response;
    }

    public java.util.List<Coupon> getActiveCoupons() {
        if (!couponRepository.findByCode("WELCOME20").isPresent()) {
            Coupon welcomeCoupon = new Coupon();
            welcomeCoupon.setCode("WELCOME20");
            welcomeCoupon.setDiscountPercentage(new java.math.BigDecimal("20.00"));
            welcomeCoupon.setMinCartValue(new java.math.BigDecimal("0.00"));
            welcomeCoupon.setActive(true);
            welcomeCoupon.setExpiryDate(LocalDateTime.now().plusYears(10));
            couponRepository.save(welcomeCoupon);
        }

        return couponRepository.findAll().stream()
                .filter(c -> c.isActive() && (c.getExpiryDate() == null || c.getExpiryDate().isAfter(LocalDateTime.now())))
                .collect(java.util.stream.Collectors.toList());
    }
}
