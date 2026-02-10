package com.cosmate.controller;

import com.cosmate.dto.request.CreateOrderRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.OrderFullResponse;
import com.cosmate.dto.response.OrderResponse;
import com.cosmate.entity.Order;
import com.cosmate.entity.OrderCostumeSurcharge;
import com.cosmate.entity.OrderDetail;
import com.cosmate.repository.OrderCostumeSurchargeRepository;
import com.cosmate.repository.OrderDetailRepository;
import com.cosmate.repository.OrderRepository;
import com.cosmate.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final OrderCostumeSurchargeRepository orderCostumeSurchargeRepository;

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(
            @RequestParam Integer cosplayerId,
            @RequestBody CreateOrderRequest request) {
        try {
            OrderResponse resp = orderService.createOrder(cosplayerId, request);
            return ApiResponse.<OrderResponse>builder()
                    .result(resp)
                    .message("Order created")
                    .build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder()
                    .code(400)
                    .message(ex.getMessage())
                    .build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder()
                    .code(500)
                    .message("Failed to create order: " + ex.getMessage())
                    .build();
        }
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<OrderResponse> payOrder(
            @RequestParam Integer cosplayerId,
            @PathVariable Integer id,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String returnUrl) {
        try {
            OrderResponse resp = orderService.payOrder(cosplayerId, id, paymentMethod, returnUrl);
            return ApiResponse.<OrderResponse>builder()
                    .result(resp)
                    .message("Payment initiated")
                    .build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<OrderResponse>builder()
                    .code(400)
                    .message(ex.getMessage())
                    .build();
        } catch (Exception ex) {
            return ApiResponse.<OrderResponse>builder()
                    .code(500)
                    .message("Failed to process payment: " + ex.getMessage())
                    .build();
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderFullResponse> getById(@PathVariable Integer id) {
        Order o = orderRepository.findById(id).orElse(null);
        if (o == null) return ApiResponse.<OrderFullResponse>builder().code(404).message("Không tìm thấy đơn hàng").build();

        List<OrderDetail> details = orderDetailRepository.findByOrderId(id);
        List<OrderCostumeSurcharge> sur = orderCostumeSurchargeRepository.findByOrderId(id);

        OrderFullResponse resp = new OrderFullResponse();
        resp.setId(o.getId());
        resp.setCosplayerId(o.getCosplayerId());
        resp.setProviderId(o.getProviderId());
        resp.setOrderType(o.getOrderType());
        resp.setStatus(o.getStatus());
        resp.setTotalAmount(o.getTotalAmount());
        resp.setCreatedAt(o.getCreatedAt());
        resp.setDetails(details);
        resp.setSurcharges(sur);

        return ApiResponse.<OrderFullResponse>builder().result(resp).build();
    }

    // List all orders (staff role required). Simple role check via header X-User-Role for demo.
    @GetMapping
    public ApiResponse<List<OrderFullResponse>> listAll(@RequestHeader(value = "X-User-Role", required = false) String role) {
        if (role == null || !(role.equalsIgnoreCase("STAFF") || role.equalsIgnoreCase("ADMIN"))) {
            return ApiResponse.<List<OrderFullResponse>>builder().code(403).message("Không có quyền truy cập danh sách đơn").build();
        }
        List<Order> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        List<OrderFullResponse> resp = orders.stream().map(o -> {
            OrderFullResponse r = new OrderFullResponse();
            r.setId(o.getId());
            r.setCosplayerId(o.getCosplayerId());
            r.setProviderId(o.getProviderId());
            r.setOrderType(o.getOrderType());
            r.setStatus(o.getStatus());
            r.setTotalAmount(o.getTotalAmount());
            r.setCreatedAt(o.getCreatedAt());
            r.setDetails(orderDetailRepository.findByOrderId(o.getId()));
            r.setSurcharges(orderCostumeSurchargeRepository.findByOrderId(o.getId()));
            return r;
        }).collect(Collectors.toList());
        return ApiResponse.<List<OrderFullResponse>>builder().result(resp).build();
    }

    // List orders by provider id
    @GetMapping("/provider/{providerId}")
    public ApiResponse<List<OrderFullResponse>> listByProvider(@PathVariable Integer providerId) {
        List<Order> orders = orderRepository.findByProviderIdOrderByCreatedAtDesc(providerId);
        List<OrderFullResponse> resp = orders.stream().map(o -> {
            OrderFullResponse r = new OrderFullResponse();
            r.setId(o.getId());
            r.setCosplayerId(o.getCosplayerId());
            r.setProviderId(o.getProviderId());
            r.setOrderType(o.getOrderType());
            r.setStatus(o.getStatus());
            r.setTotalAmount(o.getTotalAmount());
            r.setCreatedAt(o.getCreatedAt());
            r.setDetails(orderDetailRepository.findByOrderId(o.getId()));
            r.setSurcharges(orderCostumeSurchargeRepository.findByOrderId(o.getId()));
            return r;
        }).collect(Collectors.toList());
        return ApiResponse.<List<OrderFullResponse>>builder().result(resp).build();
    }
}
