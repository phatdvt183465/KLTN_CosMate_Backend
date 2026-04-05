package com.cosmate.service;

import com.cosmate.dto.request.OrderExtendRequest;
import com.cosmate.dto.response.OrderExtendResponse;

public interface OrderExtendService {
    OrderExtendResponse requestExtend(Integer userId, Integer orderId, Integer orderDetailId, OrderExtendRequest req) throws Exception;
    OrderExtendResponse payExtend(Integer userId, Integer orderId, Integer extendId, String paymentMethod, String returnUrl) throws Exception;
    OrderExtendResponse cancelExtend(Integer userId, Integer orderId, Integer extendId) throws Exception;
}

