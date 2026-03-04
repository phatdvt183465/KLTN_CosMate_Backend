package com.cosmate.service;

import com.cosmate.dto.request.CreateOrderRequest;
import com.cosmate.dto.response.OrderDropdownResponse;
import com.cosmate.dto.response.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(Integer cosplayerId, CreateOrderRequest request) throws Exception;
    // Pay an existing order if its status is UNPAID. Returns OrderResponse with paymentUrl if external flow created.
    OrderResponse payOrder(Integer cosplayerId, Integer orderId, String paymentMethod, String returnUrl) throws Exception;

    // New: list orders in a compact form for dropdowns filtered by orderType and statuses. Optionally filter by providerId or cosplayerId.
    List<OrderDropdownResponse> listOrdersForDropdown(String orderType, List<String> statuses, Integer providerId, Integer cosplayerId);
}
