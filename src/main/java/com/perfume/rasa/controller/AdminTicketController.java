package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.model.ContactTicket;
import com.perfume.rasa.model.ContactTicketEvent;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.ContactTicketRepository;
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
@RequestMapping("/api/admin/tickets")
public class AdminTicketController {

    @Autowired
    private ContactTicketRepository contactTicketRepository;

    @Autowired
    private UserRepository userRepository;

    private boolean isAdminOrEmployee(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        Optional<User> userOpt = userRepository.findByEmail(auth.getName());
        return userOpt.isPresent() && (userOpt.get().getRole() == User.Role.ADMIN || userOpt.get().getRole() == User.Role.EMPLOYEE);
    }

    @GetMapping
    public ResponseEntity<?> getAllTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL") String status,
            Authentication authentication) {

        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<ContactTicket> ticketsPage;

            if ("ALL".equalsIgnoreCase(status)) {
                ticketsPage = contactTicketRepository.findAllByOrderByCreatedAtDesc(pageable);
            } else {
                ticketsPage = contactTicketRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase(), pageable);
            }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("content", ticketsPage.getContent());
            responseData.put("totalPages", ticketsPage.getTotalPages());
            responseData.put("totalElements", ticketsPage.getTotalElements());
            responseData.put("currentPage", ticketsPage.getNumber());
            responseData.put("size", ticketsPage.getSize());

            return ResponseEntity.ok().body(new ApiResponse(true, "Tickets retrieved successfully", responseData));

        } catch (Exception e) {
            log.error("Error retrieving admin tickets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @PutMapping("/{ticketId}/status")
    public ResponseEntity<?> updateTicketStatus(
            @PathVariable String ticketId,
            @RequestParam String status,
            @RequestParam(required = false) String note,
            Authentication authentication) {

        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            Optional<ContactTicket> ticketOpt = contactTicketRepository.findByTicketId(ticketId);
            if (ticketOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Ticket not found", null));
            }

            ContactTicket ticket = ticketOpt.get();
            ticket.setStatus(status.toUpperCase());
            
            ContactTicketEvent event = new ContactTicketEvent(
                ticket,
                "STATUS_UPDATE",
                status.toUpperCase(),
                note != null && !note.trim().isEmpty() ? note : "Status updated to " + status.toUpperCase() + " by Admin"
            );
            ticket.addEvent(event);
            
            contactTicketRepository.save(ticket);
            
            return ResponseEntity.ok().body(new ApiResponse(true, "Ticket status updated successfully", null));

        } catch (Exception e) {
            log.error("Error updating ticket status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }
    @PostMapping
    public ResponseEntity<?> createTicket(@RequestBody ContactTicket ticket, Authentication authentication) {
        // if (!isAdminOrEmployee(authentication)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        // }

        try {
            if (ticket.getName() == null || ticket.getEmail() == null || ticket.getSubject() == null || ticket.getMessage() == null) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Name, Email, Subject, and Message are required", null));
            }

            ticket.setTicketId("TKT" + System.currentTimeMillis());
            ticket.setStatus("OPEN");
            
            ContactTicketEvent event = new ContactTicketEvent(
                ticket,
                "CREATED",
                "OPEN",
                "Enquiry manually created by Admin"
            );
            ticket.addEvent(event);
            
            contactTicketRepository.save(ticket);
            
            return ResponseEntity.ok().body(new ApiResponse(true, "Enquiry created successfully", null));

        } catch (Exception e) {
            log.error("Error creating admin ticket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }
}
