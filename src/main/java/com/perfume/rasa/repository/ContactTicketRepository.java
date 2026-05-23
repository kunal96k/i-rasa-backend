package com.perfume.rasa.repository;

import com.perfume.rasa.model.ContactTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactTicketRepository extends JpaRepository<ContactTicket, Long> {
    List<ContactTicket> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<ContactTicket> findByTicketId(String ticketId);
}
