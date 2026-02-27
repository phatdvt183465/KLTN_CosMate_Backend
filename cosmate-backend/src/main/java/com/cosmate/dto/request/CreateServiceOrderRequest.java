package com.cosmate.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateServiceOrderRequest {
    private Integer serviceId;
    // ISO date string: yyyy-MM-dd
    private String bookingDate;
    private String timeSlot;
    private Integer numberOfHuman;
    private BigDecimal depositSlotAmount;
    private BigDecimal rentSlotAmount;
    // the cosplayer (customer) for whom the provider creates the booking
    private Integer cosplayerId;
}
