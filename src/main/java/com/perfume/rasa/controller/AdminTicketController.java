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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            Authentication authentication) {

        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }

        try {
            String normalizedSortBy = "createdAt";
            if (Arrays.asList("ticketId", "name", "status", "createdAt", "orderId").contains(sortBy)) {
                normalizedSortBy = sortBy;
            }
            Sort.Direction direction = "ASC".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, normalizedSortBy));

            Specification<ContactTicket> spec = Specification.where(null);
            if (!"ALL".equalsIgnoreCase(status)) {
                String statusValue = status.toUpperCase();
                spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), statusValue));
            }
            if (search != null && !search.trim().isEmpty()) {
                String q = "%" + search.trim().toLowerCase() + "%";
                spec = spec.and((root, query, cb) -> cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("ticketId"), "")), q),
                        cb.like(cb.lower(cb.coalesce(root.get("name"), "")), q),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), q),
                        cb.like(cb.lower(cb.coalesce(root.get("subject"), "")), q),
                        cb.like(cb.lower(cb.coalesce(root.get("message"), "")), q),
                        cb.like(cb.lower(cb.coalesce(root.get("orderId").as(String.class), "")), q)
                ));
            }
            if (fromDate != null && !fromDate.trim().isEmpty()) {
                LocalDate startDate = LocalDate.parse(fromDate.trim());
                spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), LocalDateTime.of(startDate, LocalTime.MIN)));
            }
            if (toDate != null && !toDate.trim().isEmpty()) {
                LocalDate endDate = LocalDate.parse(toDate.trim());
                spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), LocalDateTime.of(endDate, LocalTime.MAX)));
            }

            Page<ContactTicket> ticketsPage = contactTicketRepository.findAll(spec, pageable);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("content", ticketsPage.getContent());
            responseData.put("totalPages", ticketsPage.getTotalPages());
            responseData.put("totalElements", ticketsPage.getTotalElements());
            responseData.put("currentPage", ticketsPage.getNumber());
            responseData.put("size", ticketsPage.getSize());
            responseData.put("sortBy", normalizedSortBy);
            responseData.put("sortOrder", direction.name());

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

        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }

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
        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }
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
                "Ticket manually created by Admin"
            );
            ticket.addEvent(event);
            
            contactTicketRepository.save(ticket);
            
            return ResponseEntity.ok().body(new ApiResponse(true, "Ticket created successfully", null));

        } catch (Exception e) {
            log.error("Error creating admin ticket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<?> getTicketById(@PathVariable String ticketId, Authentication authentication) {
        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }
        try {
            Optional<ContactTicket> ticketOpt = contactTicketRepository.findByTicketId(ticketId);
            if (ticketOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Ticket not found", null));
            }

            ContactTicket ticket = ticketOpt.get();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", ticket.getId());
            result.put("ticketId", ticket.getTicketId());
            result.put("name", ticket.getName());
            result.put("email", ticket.getEmail());
            result.put("mobileNo", ticket.getMobileNo());
            result.put("subject", ticket.getSubject());
            result.put("message", ticket.getMessage());
            result.put("imageUrl", ticket.getImageUrl());
            result.put("status", ticket.getStatus());
            result.put("orderId", ticket.getOrderId());
            result.put("createdAt", ticket.getCreatedAt());
            result.put("events", ticket.getEvents());

            return ResponseEntity.ok().body(new ApiResponse(true, "Ticket retrieved successfully", result));

        } catch (Exception e) {
            log.error("Error retrieving ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @PostMapping("/{ticketId}/reply")
    public ResponseEntity<?> adminReply(
            @PathVariable String ticketId,
            @RequestParam String replyMessage,
            @RequestParam(required = false, defaultValue = "IN_PROGRESS") String status,
            Authentication authentication) {
        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }
        try {
            Optional<ContactTicket> ticketOpt = contactTicketRepository.findByTicketId(ticketId);
            if (ticketOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Ticket not found", null));
            }

            if (replyMessage == null || replyMessage.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Reply message is required", null));
            }

            ContactTicket ticket = ticketOpt.get();
            ticket.setStatus(status.toUpperCase());

            ContactTicketEvent replyEvent = new ContactTicketEvent(
                ticket,
                "SUPPORT_REPLY",
                status.toUpperCase(),
                replyMessage.trim()
            );
            ticket.addEvent(replyEvent);

            contactTicketRepository.save(ticket);

            return ResponseEntity.ok().body(new ApiResponse(true, "Reply sent successfully", null));

        } catch (Exception e) {
            log.error("Error sending admin reply to ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<?> deleteTicket(@PathVariable String ticketId, Authentication authentication) {
        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }
        try {
            Optional<ContactTicket> ticketOpt = contactTicketRepository.findByTicketId(ticketId);
            if (ticketOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Ticket not found", null));
            }
            contactTicketRepository.delete(ticketOpt.get());
            return ResponseEntity.ok().body(new ApiResponse(true, "Ticket deleted successfully", null));
        } catch (Exception e) {
            log.error("Error deleting ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getTicketStats(Authentication authentication) {
        if (!isAdminOrEmployee(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Access Denied", null));
        }
        try {
            long total = contactTicketRepository.count();
            long open = contactTicketRepository.countByStatus("OPEN");
            long inProgress = contactTicketRepository.countByStatus("IN_PROGRESS");
            long resolved = contactTicketRepository.countByStatus("RESOLVED");

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total", total);
            stats.put("open", open);
            stats.put("inProgress", inProgress);
            stats.put("resolved", resolved);

            return ResponseEntity.ok().body(new ApiResponse(true, "Stats retrieved successfully", stats));

        } catch (Exception e) {
            log.error("Error retrieving ticket stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Internal Server Error", null));
        }
    }
}
