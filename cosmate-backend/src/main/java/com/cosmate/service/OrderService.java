package com.cosmate.service;

import com.cosmate.dto.request.CreateOrderRequest;
import com.cosmate.dto.request.CreateServiceOrderRequest;
import com.cosmate.dto.response.OrderDropdownResponse;
import com.cosmate.dto.response.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(Integer cosplayerId, CreateOrderRequest request) throws Exception;
    // Pay an existing order if its status is UNPAID. Returns OrderResponse with paymentUrl if external flow created.
    OrderResponse payOrder(Integer cosplayerId, Integer orderId, String paymentMethod, String returnUrl) throws Exception;

    // Service-order specific operations (previously implemented in controllers)
    OrderResponse providerCreateBooking(Integer providerUserId, CreateServiceOrderRequest req) throws Exception;
    OrderResponse confirmServiceOrderByCosplayer(Integer cosplayerUserId, Integer orderId) throws Exception;
    OrderResponse providerSetWaiting(Integer providerUserId, Integer orderId) throws Exception;
    OrderResponse startServiceNow(Integer providerUserId, Integer orderId) throws Exception;
    OrderResponse providerCompleteService(Integer providerUserId, Integer orderId) throws Exception;
    OrderResponse cancelOrder(Integer userId, Integer orderId) throws Exception;
    java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse> listProviderServiceOrders(Integer providerUserId, String statuses) throws Exception;
    // Full order operations moved from controller
    com.cosmate.dto.response.OrderFullResponse getFullOrderById(Integer id) throws Exception;
    java.util.List<com.cosmate.dto.response.OrderFullResponse> listAllOrders() throws Exception;
    java.util.List<com.cosmate.dto.response.OrderFullResponse> listOrdersByProvider(Integer providerId) throws Exception;
    java.util.List<com.cosmate.dto.response.OrderFullResponse> filterOrdersByProviderAndStatuses(Integer providerId, java.util.List<String> statuses, Integer currentUserId, boolean isAdminStaff) throws Exception;
    java.util.List<com.cosmate.dto.response.OrderFullResponse> listOrdersByUserId(Integer userId) throws Exception;
    // ship/delivery/return flows
    java.util.Map<String,Object> shipOrder(Integer currentUserId, Integer id, String trackingCode, org.springframework.web.multipart.MultipartFile[] images, java.util.List<String> notes, boolean isAdminStaff) throws Exception;
    java.util.Map<String,Object> markDeliveringOut(Integer currentUserId, Integer id, boolean isAdminStaff) throws Exception;
    java.util.Map<String,Object> confirmDelivery(Integer currentUserId, Integer id, org.springframework.web.multipart.MultipartFile[] images, java.util.List<String> notes) throws Exception;
    String prepareOrder(Integer currentUserId, Integer id, boolean isAdminStaff) throws Exception;
    java.util.Map<String,Object> startReturn(Integer currentUserId, Integer id, String trackingCode, org.springframework.web.multipart.MultipartFile[] images, java.util.List<String> notes) throws Exception;
    java.util.Map<String,Object> completeOrder(Integer currentUserId, Integer id, boolean isAdminStaff) throws Exception;
    java.util.List<com.cosmate.dto.response.TransactionResponse> getTransactionsForOrder(Integer id) throws Exception;

    // New: list orders in a compact form for dropdowns filtered by orderType and statuses. Optionally filter by providerId or cosplayerId.
    List<OrderDropdownResponse> listOrdersForDropdown(String orderType, List<String> statuses, Integer providerId, Integer cosplayerId);
}
