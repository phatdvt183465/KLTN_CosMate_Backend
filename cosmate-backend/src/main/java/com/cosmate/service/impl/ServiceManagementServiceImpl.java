package com.cosmate.service.impl;

import com.cosmate.dto.request.ServiceRequest;
import com.cosmate.dto.response.ServiceResponse;
import com.cosmate.entity.Service;
import com.cosmate.entity.ServiceAlbum;
import com.cosmate.entity.ServiceArea;
import com.cosmate.repository.ServiceRepository;
import com.cosmate.service.FirebaseStorageService;
import com.cosmate.service.ServiceManagementService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.cosmate.service.AIService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ServiceManagementServiceImpl implements ServiceManagementService {

    private final ServiceRepository serviceRepository;
    private final ObjectMapper objectMapper;
    private final AIService aiService;
    private final FirebaseStorageService firebaseStorageService;

    @Override
    @Transactional
    public ServiceResponse createService(ServiceRequest request) {
        Service service = new Service();
        mapBaseInfo(service, request);
        service.setStatus("ACTIVE");

        handleAreas(service, request.getAreas());
        if (hasValidNewImages(request.getAlbumFiles())) {
            handleAlbums(service, request.getAlbumFiles());
        }

        return mapToResponse(serviceRepository.save(service));
    }

    @Override
    @Transactional
    public ServiceResponse updateService(Integer id, ServiceRequest request) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dịch vụ!"));

        // PHẦN 1: CẬP NHẬT THÔNG TIN CƠ BẢN VÀ KHU VỰC
        updateBasicInfo(service, request);

        // PHẦN 2: CẬP NHẬT ẢNH
        if (hasValidNewImages(request.getAlbumFiles())) {
            service.getAlbums().clear(); // Xóa sạch record ảnh cũ trong DB
            handleAlbums(service, request.getAlbumFiles()); // Up ảnh mới và lưu DB
        }

        return mapToResponse(serviceRepository.save(service));
    }

    @Override
    public List<ServiceResponse> getByProviderId(Integer providerId) {
        return serviceRepository.findByProviderIdAndStatus(providerId, "ACTIVE").stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ServiceResponse getById(Integer id) {
        return mapToResponse(serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dịch vụ!")));
    }

    @Override
    @Transactional
    public void deleteService(Integer id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dịch vụ!"));
        service.setStatus("DELETED"); // Xóa mềm
        serviceRepository.save(service);
    }

    // =========================================================
    // --- CÁC HÀM TIỆN ÍCH (HELPERS) TÁCH RỜI LOGIC ---
    // =========================================================

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    // Hàm 1: Chỉ chuyên lo việc cập nhật chữ và số
    private void updateBasicInfo(Service service, ServiceRequest request) {
        if (hasText(request.getServiceType())) service.setServiceType(request.getServiceType());
        if (hasText(request.getDescription())) service.setDescription(request.getDescription());
        if (request.getSlotDurationHours() != null) service.setSlotDurationHours(request.getSlotDurationHours());
        if (request.getPricePerSlot() != null) service.setPricePerSlot(request.getPricePerSlot());
        if (request.getMinPrice() != null) service.setMinPrice(request.getMinPrice());
        if (request.getMaxPrice() != null) service.setMaxPrice(request.getMaxPrice());
        if (request.getEquipmentDepreciationCost() != null)
            service.setEquipmentDepreciationCost(request.getEquipmentDepreciationCost());
        if (request.getDepositAmount() != null) service.setDepositAmount(request.getDepositAmount());

        if (hasText(request.getAreas())) {
            service.getAreas().clear();
            handleAreas(service, request.getAreas());
        }
    }

    // Hàm 2: Thám tử kiểm tra xem mảng File truyền lên có thật sự chứa ảnh không
    private boolean hasValidNewImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return false;
        for (MultipartFile file : files) {
            // Phải khác null và kích thước > 0 mới tính là có ảnh thật
            if (file != null && !file.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void handleAreas(Service service, String json) {
        if (!hasText(json)) return;
        try {
            List<Map<String, String>> areaData = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, String> data : areaData) {
                ServiceArea area = new ServiceArea();
                area.setCity(data.get("city"));
                area.setDistrict(data.get("district"));
                area.setService(service);
                service.getAreas().add(area);
            }
        } catch (IOException e) {
            throw new RuntimeException("JSON lỗi rồi đại ca ơi! Check lại format: [{\"city\":\"...\",\"district\":\"...\"}]");
        }
    }

    private void handleAlbums(Service service, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;

//        // AI soi ảnh 18+
//        aiService.validateMultipleImageContents(files);
        
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            try {
                // Xử lý tên file cho an toàn
                String original = file.getOriginalFilename();
                String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");

                // Build đường dẫn: services/{serviceId}/{timestamp}_{safeName}
                // (Vì lúc tạo mới service chưa có ID nên mình dùng tạm System.currentTimeMillis() làm folder)
                String folderName = service.getId() != null ? String.valueOf(service.getId()) : "new_" + System.currentTimeMillis();
                String path = String.format("services/%s/%d_%s", folderName, System.currentTimeMillis(), safeName);

                // Gọi hàm để up và lấy link Public
                String imageUrl = firebaseStorageService.uploadFile(file, path);

                // Lưu vào DB
                ServiceAlbum album = new ServiceAlbum();
                album.setImageUrl(imageUrl);
                album.setService(service);
                service.getAlbums().add(album);

            } catch (Exception e) {
                throw new RuntimeException("Upload ảnh xịt rồi: " + e.getMessage());
            }
        }
    }

    private void mapBaseInfo(Service s, ServiceRequest r) {
        s.setServiceType(r.getServiceType());
        s.setDescription(r.getDescription());
        s.setSlotDurationHours(r.getSlotDurationHours());
        s.setPricePerSlot(r.getPricePerSlot());
        s.setMinPrice(r.getMinPrice());
        s.setMaxPrice(r.getMaxPrice());
        s.setEquipmentDepreciationCost(r.getEquipmentDepreciationCost());
        s.setProviderId(r.getProviderId());
        s.setDepositAmount(r.getDepositAmount());
    }

    private ServiceResponse mapToResponse(Service s) {
        return ServiceResponse.builder()
                .id(s.getId())
                .serviceType(s.getServiceType())
                .description(s.getDescription())
                .slotDurationHours(s.getSlotDurationHours())
                .pricePerSlot(s.getPricePerSlot())
                .minPrice(s.getMinPrice())
                .maxPrice(s.getMaxPrice())
                .equipmentDepreciationCost(s.getEquipmentDepreciationCost())
                .status(s.getStatus())
                .providerId(s.getProviderId())
                .depositAmount(s.getDepositAmount())
                .areas(s.getAreas().stream()
                        .map(a -> a.getDistrict() + ", " + a.getCity())
                        .collect(Collectors.toList()))
                .imageUrls(s.getAlbums().stream()
                        .map(ServiceAlbum::getImageUrl)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    public List<ServiceResponse> getAllServices() {
        // Thường lấy các service ACTIVE để hiển thị trên trang chủ/danh sách chung
        return serviceRepository.findByStatus("ACTIVE").stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ServiceResponse> getAllByProviderId(Integer providerId) {
        // Lấy tất cả dịch vụ của 1 provider (thường dùng cho trang quản lý của Provider)
        // Không lọc status "ACTIVE" để họ thấy cả những cái đã xóa mềm hoặc đang ẩn
        return serviceRepository.findByProviderId(providerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ServiceResponse> getByServiceType(String serviceType) {
        // Lấy danh sách service đang ACTIVE theo loại (VD: Makeup, Photographer...)
        return serviceRepository.findByServiceTypeAndStatus(serviceType, "ACTIVE").stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
}