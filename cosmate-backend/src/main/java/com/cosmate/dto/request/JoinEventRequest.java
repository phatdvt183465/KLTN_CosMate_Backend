package com.cosmate.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class JoinEventRequest {
    private Integer eventId;
    private Integer cosplayerId;
    private MultipartFile submissionImage; // Ảnh dự thi gửi lên để up Firebase
}