package com.cosmate.service;

import com.cosmate.dto.response.OrderTrackingResponse;

import java.util.List;

public interface OrderTrackingService {
    List<OrderTrackingResponse> listByOrder(Integer orderId);
    OrderTrackingResponse getById(Integer id);
    OrderTrackingResponse updateTrackingCode(Integer id, String trackingCode, String shippingCarrierName);
}

