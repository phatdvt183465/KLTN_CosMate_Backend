package com.cosmate.dto.request;

import lombok.Data;

@Data
public class VoteRequest {
    private Integer eventId;
    private Integer participantId; // ID của bài dự thi đang được vote
    private Integer voterId; // ID của người đang bấm vote
}