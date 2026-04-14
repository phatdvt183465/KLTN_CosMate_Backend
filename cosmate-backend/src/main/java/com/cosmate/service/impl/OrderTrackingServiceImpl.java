package com.cosmate.service.impl;

import com.cosmate.dto.response.OrderTrackingResponse;
import com.cosmate.entity.OrderTracking;
import com.cosmate.repository.OrderRepository;
import com.cosmate.repository.OrderTrackingRepository;
import com.cosmate.service.OrderTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderTrackingServiceImpl implements OrderTrackingService {

    private final OrderTrackingRepository orderTrackingRepository;
    private final OrderRepository orderRepository;

    @Override
    public List<OrderTrackingResponse> listByOrder(Integer orderId) {
        if (!orderRepository.existsById(orderId)) throw new IllegalArgumentException("Order not found");
        return orderTrackingRepository.findByOrderId(orderId).stream()
                .filter(t -> t.getTrackingCode() != null && !t.getTrackingCode().isBlank())
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public OrderTrackingResponse getById(Integer id) {
        OrderTracking t = orderTrackingRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Tracking entry not found"));
        return toResponse(t);
    }

    @Override
    public OrderTrackingResponse updateTrackingCode(Integer id, String trackingCode, String shippingCarrierName) {
        OrderTracking t = orderTrackingRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Tracking entry not found"));
        if (trackingCode == null || trackingCode.isBlank()) throw new IllegalArgumentException("trackingCode is required");
        t.setTrackingCode(trackingCode);
        t.setShippingCarrierName(shippingCarrierName);
        OrderTracking saved = orderTrackingRepository.save(t);
        return toResponse(saved);
    }

    private OrderTrackingResponse toResponse(OrderTracking t) {
        return OrderTrackingResponse.builder()
                .id(t.getId())
                .orderId(t.getOrder() == null ? null : t.getOrder().getId())
                .trackingCode(t.getTrackingCode())
                .trackingStatus(t.getTrackingStatus())
                .stage(t.getStage())
                .shippingCarrierName(t.getShippingCarrierName())
                .createdAt(t.getCreatedAt())
                .build();
    }
}

