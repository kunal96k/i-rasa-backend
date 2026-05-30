package com.perfume.rasa.repository;

import com.perfume.rasa.model.Enquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {
    Page<Enquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Enquiry> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
