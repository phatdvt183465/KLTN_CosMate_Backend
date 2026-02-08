package com.cosmate.service.impl;

import com.cosmate.entity.Costume;
import com.cosmate.entity.CostumeSurcharge;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.CostumeSurchargeRepository;
import com.cosmate.service.CostumeSurchargeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CostumeSurchargeServiceImpl implements CostumeSurchargeService {

    private final CostumeSurchargeRepository surchargeRepository;
    private final CostumeRepository costumeRepository;

    @Override
    public List<CostumeSurcharge> getByCostumeId(Long costumeId) {
        // Tìm danh sách phụ phí liên quan đến Costume ID
        return surchargeRepository.findByCostumeId(costumeId);
    }

    @Override
    @Transactional
    public CostumeSurcharge create(Long costumeId, CostumeSurcharge request) {
        // Kiểm tra xem Costume có tồn tại không trước khi thêm phụ phí
        Costume costume = costumeRepository.findById(costumeId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bộ đồ ID: " + costumeId + " để thêm phí!"));

        request.setCostume(costume); // Gán quan hệ
        return surchargeRepository.save(request);
    }

    @Override
    @Transactional
    public CostumeSurcharge update(Long id, CostumeSurcharge request) {
        // Tìm phụ phí cũ để cập nhật
        CostumeSurcharge surcharge = surchargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Phụ phí này không tồn tại!"));

        if (request.getName() != null && !request.getName().isBlank())
            surcharge.setName(request.getName());

        if (request.getPrice() != null)
            surcharge.setPrice(request.getPrice());

        if (request.getDescription() != null)
            surcharge.setDescription(request.getDescription());

        return surchargeRepository.save(surcharge);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!surchargeRepository.existsById(id)) {
            throw new RuntimeException("Phí này xóa rồi hoặc không có!");
        }
        surchargeRepository.deleteById(id);
    }
}