package com.perfume.rasa.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contact_ticket_events")
public class ContactTicketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    @JsonIgnore
    private ContactTicket ticket;

    private String eventType; // STATUS_UPDATE, CUSTOMER_COMMENT, SUPPORT_REPLY
    private String status;      // OPEN, IN_PROGRESS, RESOLVED
    
    @Column(length = 2000)
    private String note;

    private LocalDateTime createdAt = LocalDateTime.now();

    public ContactTicketEvent() {}

    public ContactTicketEvent(ContactTicket ticket, String eventType, String status, String note) {
        this.ticket = ticket;
        this.eventType = eventType;
        this.status = status;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ContactTicket getTicket() { return ticket; }
    public void setTicket(ContactTicket ticket) { this.ticket = ticket; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
