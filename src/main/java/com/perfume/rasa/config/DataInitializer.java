package com.perfume.rasa.config;

import com.perfume.rasa.model.Coupon;
import com.perfume.rasa.repository.CouponRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final CouponRepository couponRepository;

    public DataInitializer(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // ── CLEAN UP: Remove ALL coupons except REFIL100 ──
        List<Coupon> allCoupons = couponRepository.findAll();
        for (Coupon c : allCoupons) {
            if (!"REFIL100".equalsIgnoreCase(c.getCode())) {
                couponRepository.delete(c);
            }
        }

        // ── Ensure REFIL100 exists and is properly configured ──
        Coupon refillCoupon = couponRepository.findByCode("REFIL100").orElseGet(Coupon::new);
        refillCoupon.setCode("REFIL100");
        // NOTE: discountAmount stored in DB but frontend treats REFIL100 as invoice-only (no price deduction)
        refillCoupon.setDiscountAmount(new BigDecimal("100.00"));
        refillCoupon.setMinCartValue(null);  // No minimum order required
        refillCoupon.setActive(true);
        refillCoupon.setExpiryDate(LocalDateTime.of(2099, 12, 31, 23, 59, 59));
        refillCoupon.setDescription("Bring your empty bottle to our store to get \u20b9100 off on your next refill. This coupon is noted on your invoice.");
        refillCoupon.setValidity("All Time");
        refillCoupon.setDiscount("\u267b\ufe0f Bottle Refill Offer");

        couponRepository.save(refillCoupon);
    }
}
