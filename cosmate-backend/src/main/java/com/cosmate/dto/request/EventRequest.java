package com.cosmate.dto.request;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventRequest {
    private String title;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status; // changed code: status is a String representing enum-like values (e.g., 'UPCOMING')
    private Integer createdBy; // ID của Staff/Admin tạo sự kiện
}