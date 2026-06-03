package com.perfume.rasa.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OrderStatusHistoryDTO {
    private String status;
    private String notes;
    private LocalDateTime timestamp;
}
