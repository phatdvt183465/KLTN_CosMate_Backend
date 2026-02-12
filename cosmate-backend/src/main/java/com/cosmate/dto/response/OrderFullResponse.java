package com.cosmate.dto.response;

import com.cosmate.entity.OrderCostumeSurcharge;
import com.cosmate.entity.OrderDetail;
import com.cosmate.entity.OrderAddress;
import com.cosmate.entity.OrderDetailAccessory;
import com.cosmate.entity.OrderRentalOption;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderFullResponse {
    private Integer id;
    private Integer cosplayerId;
    private Integer providerId;
    private String orderType;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;

    private List<OrderDetail> details;
    private List<OrderCostumeSurcharge> surcharges;
    private List<OrderAddress> addresses;
    private List<OrderDetailAccessory> accessories;
    private List<OrderRentalOption> rentalOptions;
}
