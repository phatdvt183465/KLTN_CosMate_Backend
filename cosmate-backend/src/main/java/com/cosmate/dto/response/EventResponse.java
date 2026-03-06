package com.cosmate.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    private Integer id;
    private String title;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer createdBy;

    private List<ParticipantDto> participants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantDto {
        private Integer participantId;
        private Integer cosplayerId;
        private String submissionImageUrl;
        private Integer totalVotes; // Frontend sẽ dùng cái này để xếp hạng 1, 2, 3
    }
}