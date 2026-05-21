package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.dto.CouponValidateRequest;
import com.perfume.rasa.dto.CouponValidateResponse;
import com.perfume.rasa.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    @Autowired
    private CouponService couponService;

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse> validateCoupon(
            @RequestBody CouponValidateRequest request,
            org.springframework.security.core.Authentication authentication) {
        try {
            CouponValidateResponse response = couponService.validateCoupon(request, authentication);
            return ResponseEntity.ok(new ApiResponse(true, "Coupon applied successfully", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getActiveCoupons() {
        try {
            return ResponseEntity.ok(new ApiResponse(true, "Active coupons retrieved successfully", couponService.getActiveCoupons()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage(), null));
        }
    }
}
