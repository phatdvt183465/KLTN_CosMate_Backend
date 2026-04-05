package com.cosmate.controller;

import com.cosmate.dto.request.OrderTrackingRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.OrderTrackingResponse;
import com.cosmate.entity.OrderTracking;
import com.cosmate.service.OrderTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/order-tracking")
@RequiredArgsConstructor
public class OrderTrackingController {

    private final OrderTrackingService orderTrackingService;

    // List tracking entries for an order (only returns entries with non-null trackingCode)
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<List<OrderTrackingResponse>>> listByOrder(@PathVariable Integer orderId) {
        List<OrderTrackingResponse> resp = orderTrackingService.listByOrder(orderId);
        return ResponseEntity.ok(ApiResponse.<List<OrderTrackingResponse>>builder().result(resp).build());
    }

    // Get a single tracking entry by id
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderTrackingResponse>> getById(@PathVariable Integer id) {
        OrderTrackingResponse resp = orderTrackingService.getById(id);
        return ResponseEntity.ok(ApiResponse.<OrderTrackingResponse>builder().result(resp).build());
    }

    // Update only the trackingCode by tracking id
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderTrackingResponse>> updateTrackingCode(@PathVariable Integer id, @RequestBody OrderTrackingRequest req) {
        OrderTrackingResponse resp = orderTrackingService.updateTrackingCode(id, req.getTrackingCode());
        return ResponseEntity.ok(ApiResponse.<OrderTrackingResponse>builder().result(resp).build());
    }

    // mapping handled in service
}


