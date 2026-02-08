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
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private Integer createdBy; // ID của Staff/Admin tạo sự kiện
}