package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.model.Coupon;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.CouponRepository;
import com.perfume.rasa.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponController {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    private boolean isAdminOrEmployee(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        Optional<User> userOpt = userRepository.findByEmail(auth.getName());
        return userOpt.isPresent() && (userOpt.get().getRole() == User.Role.ADMIN || userOpt.get().getRole() == User.Role.EMPLOYEE);
    }

    @GetMapping
    public ResponseEntity<?> getAllCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Coupon> couponsPage = couponRepository.findAll(pageable);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("content", couponsPage.getContent());
            responseData.put("totalPages", couponsPage.getTotalPages());
            responseData.put("totalElements", couponsPage.getTotalElements());
            responseData.put("currentPage", couponsPage.getNumber());
            responseData.put("size", couponsPage.getSize());

            return ResponseEntity.ok().body(new ApiResponse(true, "Coupons retrieved successfully", responseData));

        } catch (Exception e) {
            log.error("Error retrieving admin coupons", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @PostMapping
    public ResponseEntity<?> createCoupon(@RequestBody Coupon coupon, Authentication authentication) {
        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            if (coupon.getCode() == null || coupon.getCode().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Coupon code is required", null));
            }

            // check duplicate
            Optional<Coupon> existing = couponRepository.findByCode(coupon.getCode());
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Coupon code already exists", null));
            }

            coupon.setCode(coupon.getCode().toUpperCase().trim());
            Coupon saved = couponRepository.save(coupon);

            return ResponseEntity.ok().body(new ApiResponse(true, "Coupon created successfully", saved));

        } catch (Exception e) {
            log.error("Error creating coupon", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> toggleCouponStatus(@PathVariable Long id, @RequestParam boolean active, Authentication authentication) {
        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            Optional<Coupon> couponOpt = couponRepository.findById(id);
            if (couponOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Coupon not found", null));
            }

            Coupon coupon = couponOpt.get();
            coupon.setActive(active);
            couponRepository.save(coupon);

            return ResponseEntity.ok().body(new ApiResponse(true, "Coupon status updated successfully", null));

        } catch (Exception e) {
            log.error("Error toggling coupon status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCoupon(@PathVariable Long id, Authentication authentication) {
        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            Optional<Coupon> couponOpt = couponRepository.findById(id);
            if (couponOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Coupon not found", null));
            }

            couponRepository.delete(couponOpt.get());
            return ResponseEntity.ok().body(new ApiResponse(true, "Coupon deleted successfully", null));

        } catch (Exception e) {
            log.error("Error deleting coupon", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }
}
