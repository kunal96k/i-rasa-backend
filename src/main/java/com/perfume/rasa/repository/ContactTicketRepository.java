package com.perfume.rasa.repository;

import com.perfume.rasa.model.ContactTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContactTicketRepository extends JpaRepository<ContactTicket, Long> {
    List<ContactTicket> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<ContactTicket> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<ContactTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<ContactTicket> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    Optional<ContactTicket> findByTicketId(String ticketId);
}
