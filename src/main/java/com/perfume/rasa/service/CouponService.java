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

        // Check perfume eligibility for refill coupons
        if ("REFIL100".equalsIgnoreCase(coupon.getCode()) || "REFILL100".equalsIgnoreCase(coupon.getCode())) {
            boolean hasPerfume = false;
            if (request.getItems() != null) {
                for (com.perfume.rasa.dto.OrderItemRequestDTO item : request.getItems()) {
                    if (isPerfume(item.getProductId()) || isPerfume(item.getName())) {
                        hasPerfume = true;
                        break;
                    }
                }
            }
            if (!hasPerfume) {
                throw new RuntimeException("Coupon is only eligible for orders containing perfumes.");
            }
        }

        // Check eligibility for PERFUME79 (requires at least 2x 60ml perfume bottles)
        if ("PERFUME79".equalsIgnoreCase(coupon.getCode())) {
            int perfume60mlCount = 0;
            if (request.getItems() != null) {
                for (com.perfume.rasa.dto.OrderItemRequestDTO item : request.getItems()) {
                    if (isPerfume(item.getProductId()) || isPerfume(item.getName())) {
                        String itemSize = item.getSize() != null ? item.getSize().toLowerCase().trim() : "";
                        String itemName = item.getName() != null ? item.getName().toLowerCase().trim() : "";
                        
                        // Check if it's a 60ml bottle (or default perfume size)
                        boolean is60ml = itemSize.isEmpty() 
                                         || itemSize.contains("60ml") 
                                         || itemSize.contains("60 ml") 
                                         || itemName.contains("60ml") 
                                         || itemName.contains("60 ml")
                                         || !itemSize.contains("100ml");
                        
                        if (is60ml) {
                            int itemQty = item.getQty() != null ? item.getQty() : 1;
                            perfume60mlCount += itemQty;
                        }
                    }
                }
            }
            if (perfume60mlCount < 2) {
                throw new RuntimeException("PERFUME79 coupon requires purchasing at least 2x 60ml perfume bottles.");
            }
        }

        // Check eligibility for ATTAR79 (requires 6ml Attar bottle)
        if ("ATTAR79".equalsIgnoreCase(coupon.getCode())) {
            boolean hasAttar = false;
            if (request.getItems() != null) {
                for (com.perfume.rasa.dto.OrderItemRequestDTO item : request.getItems()) {
                    if (!isPerfume(item.getProductId()) && !isPerfume(item.getName())) {
                        hasAttar = true;
                        break;
                    }
                }
            }
            if (!hasAttar) {
                throw new RuntimeException("ATTAR79 coupon is only eligible for orders containing 6ml Attar.");
            }
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

    private boolean isPerfume(String name) {
        if (name == null) return false;
        String nameLower = name.toLowerCase().trim();
        
        // List of all known attar names (case-insensitive) from attar.html
        java.util.Set<String> knownAttars = java.util.Set.of(
            "dove", "mogra (attarfull)", "kasturi", "white london", "green musk", 
            "latafa khamrha kawa", "parijat", "darbar", "gold sandel", "ice blue", 
            "sonchafa", "ambar oud", "shanaya gold", "shanaya", "ponds", "charli black", 
            "chocolate", "vanilla", "tulsi", "kapoor", "dalchini", "ratrani", 
            "musk a tahara", "whtie sandel", "mhaisur sandal", "kevd", "kesharchandan", 
            "musk rose", "black rose", "ice barg", "belpaan", "musk saffi", 
            "london light", "heena", "lemongrass", "lemon", "oreng", "pineapple", 
            "cigar", "coffee", "jasmine", "lavender", "open", "green ajmeri", 
            "555", "white oud", "arabic oud", "blackberry", "kala bhoot"
        );
        
        return !knownAttars.contains(nameLower);
    }

    public java.util.List<Coupon> getActiveCoupons() {
        return couponRepository.findAll().stream()
                .filter(c -> c.isActive()
                        && (c.getExpiryDate() == null || c.getExpiryDate().isAfter(LocalDateTime.now())))
                .collect(java.util.stream.Collectors.toList());
    }
}
