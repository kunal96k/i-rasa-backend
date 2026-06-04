package com.perfume.rasa.repository;

import com.perfume.rasa.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCode(String code);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Coupon c WHERE " +
            "LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    org.springframework.data.domain.Page<Coupon> searchCoupons(
            @org.springframework.data.repository.query.Param("search") String search, 
            org.springframework.data.domain.Pageable pageable);
}
