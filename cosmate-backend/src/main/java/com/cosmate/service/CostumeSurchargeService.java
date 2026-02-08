package com.cosmate.service;

import com.cosmate.entity.CostumeSurcharge;
import java.util.List;

public interface CostumeSurchargeService {
    // Lấy danh sách phụ phí theo ID bộ đồ
    List<CostumeSurcharge> getByCostumeId(Long costumeId);

    // Tạo mới phụ phí cho một bộ đồ cụ thể
    CostumeSurcharge create(Long costumeId, CostumeSurcharge request);

    // Cập nhật thông tin phụ phí
    CostumeSurcharge update(Long id, CostumeSurcharge request);

    // Xóa phụ phí
    void delete(Long id);
}