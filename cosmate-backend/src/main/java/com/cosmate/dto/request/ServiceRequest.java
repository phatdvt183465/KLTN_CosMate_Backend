package com.cosmate.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ServiceRequest {
    private String serviceType; // Tên loại dịch vụ (Makeup, Photo,...)
    private String description;
    private Integer slotDurationHours; // Số giờ mỗi slot
    private BigDecimal pricePerSlot; // Giá mỗi slot
    private BigDecimal equipmentDepreciationCost; // Chi phí khấu hao thiết bị
    private BigDecimal depositAmount;
    private Integer providerId;
    private String areas; // JSON chuỗi: [{"city":"...","district":"..."}]
    private List<MultipartFile> albumFiles;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}