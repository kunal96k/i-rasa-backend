package com.perfume.rasa.repository;

import com.perfume.rasa.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId " +
           "AND (:status IS NULL OR :status = '' OR o.status = :status) " +
           "AND (o.createdAt >= :startDate OR :startDate IS NULL) " +
           "AND (o.createdAt <= :endDate OR :endDate IS NULL) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     CAST(o.id as string) LIKE CONCAT('%', :search, '%') OR " +
           "     LOWER(o.billingAddress.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     EXISTS (SELECT i FROM o.items i WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<Order> findFilteredOrders(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT o FROM Order o WHERE " +
           "(:status IS NULL OR :status = '' OR o.status = :status) " +
           "AND (o.createdAt >= :startDate OR :startDate IS NULL) " +
           "AND (o.createdAt <= :endDate OR :endDate IS NULL) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     CAST(o.id as string) LIKE CONCAT('%', :search, '%') OR " +
           "     LOWER(o.billingAddress.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     EXISTS (SELECT i FROM o.items i WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<Order> findAllFilteredOrders(
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("search") String search,
            Pageable pageable
    );
}
