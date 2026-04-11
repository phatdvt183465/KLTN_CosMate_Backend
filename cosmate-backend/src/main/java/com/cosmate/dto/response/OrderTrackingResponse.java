package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderTrackingResponse {
    private Integer id;
    private Integer orderId;
    private String trackingCode;
    private String trackingStatus;
    private String stage;
    private String shippingCarrierName;
    private LocalDateTime createdAt;
}

