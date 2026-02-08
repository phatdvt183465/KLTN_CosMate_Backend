package com.cosmate.dto.response;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    private Integer id;
    private String title;
    private String description;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private Integer createdBy;
}