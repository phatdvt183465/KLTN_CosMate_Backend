package com.cosmate.service;

import com.cosmate.dto.request.CreateOrderRequest;
import com.cosmate.dto.response.OrderResponse;

public interface OrderService {
    OrderResponse createOrder(Integer cosplayerId, CreateOrderRequest request) throws Exception;
    // Pay an existing order if its status is UNPAID. Returns OrderResponse with paymentUrl if external flow created.
    OrderResponse payOrder(Integer cosplayerId, Integer orderId, String paymentMethod, String returnUrl) throws Exception;
}
