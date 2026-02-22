package com.cosmate.service.impl;

import com.cosmate.configuration.FirebaseConfig;
import com.cosmate.dto.request.ServiceRequest;
import com.cosmate.dto.response.ServiceResponse;
import com.cosmate.entity.Service;
import com.cosmate.entity.ServiceAlbum;
import com.cosmate.entity.ServiceArea;
import com.cosmate.repository.ServiceRepository;
import com.cosmate.service.ServiceManagementService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Bucket;
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
    private final FirebaseConfig firebaseConfig;
    private final AIService aiService;

    @Override
    @Transactional
    public ServiceResponse createService(ServiceRequest request) {
        Service service = new Service();
        mapBaseInfo(service, request);
        service.setStatus("ACTIVE");

        handleAreas(service, request.getAreas());
        handleAlbums(service, request.getAlbumFiles());

        return mapToResponse(serviceRepository.save(service));
    }

    @Override
    @Transactional
    public ServiceResponse updateService(Integer id, ServiceRequest request) {
        // 1. Load dữ liệu cũ từ DB (bao gồm cả Albums cũ)
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dịch vụ!"));

        // 2. Update thông tin cơ bản (Nếu có gửi lên thì mới sửa)
        if (hasText(request.getServiceType())) service.setServiceType(request.getServiceType());
        if (hasText(request.getDescription())) service.setDescription(request.getDescription());
        if (request.getSlotDurationHours() != null) service.setSlotDurationHours(request.getSlotDurationHours());
        if (request.getPricePerSlot() != null) service.setPricePerSlot(request.getPricePerSlot());
        if (request.getEquipmentDepreciationCost() != null)
            service.setEquipmentDepreciationCost(request.getEquipmentDepreciationCost());

        // 3. Update Khu vực (Chỉ sửa nếu JSON không rỗng)
        if (hasText(request.getAreas())) {
            service.getAreas().clear();
            handleAreas(service, request.getAreas());
        }

        // 4. Update Ảnh (Logic quan trọng check null/rỗng)
        List<MultipartFile> newFiles = request.getAlbumFiles();
        boolean userWantsToChangeImage = false;

        // Kiểm tra xem user có thực sự gửi file nào có dung lượng > 0 không
        if (newFiles != null && !newFiles.isEmpty()) {
            for (MultipartFile f : newFiles) {
                if (!f.isEmpty()) {
                    userWantsToChangeImage = true;
                    break;
                }
            }
        }

        if (userWantsToChangeImage) {
            service.getAlbums().clear();
            handleAlbums(service, newFiles);
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

    // --- Helpers ---

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
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
        Bucket bucket = firebaseConfig.getBucket();
        if (bucket == null) throw new RuntimeException("Firebase chưa kết nối được!");
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            aiService.validateImageContent(file);
            try {
                String fileName = "services/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
                bucket.create(fileName, file.getBytes(), file.getContentType());

                ServiceAlbum album = new ServiceAlbum();
                album.setImageUrl("https://storage.googleapis.com/" + bucket.getName() + "/" + fileName);
                album.setService(service);
                service.getAlbums().add(album);
            } catch (IOException e) { throw new RuntimeException("Upload ảnh xịt rồi: " + e.getMessage()); }
        }
    }

    private void mapBaseInfo(Service s, ServiceRequest r) {
        s.setServiceType(r.getServiceType());
        s.setDescription(r.getDescription());
        s.setSlotDurationHours(r.getSlotDurationHours());
        s.setPricePerSlot(r.getPricePerSlot());
        s.setEquipmentDepreciationCost(r.getEquipmentDepreciationCost());
        s.setProviderId(r.getProviderId());
    }

    private ServiceResponse mapToResponse(Service s) {
        return ServiceResponse.builder()
                .id(s.getId())
                .serviceType(s.getServiceType())
                .description(s.getDescription())
                .slotDurationHours(s.getSlotDurationHours())
                .pricePerSlot(s.getPricePerSlot())
                .equipmentDepreciationCost(s.getEquipmentDepreciationCost())
                .status(s.getStatus())
                .providerId(s.getProviderId())
                .areas(s.getAreas().stream()
                        .map(a -> a.getDistrict() + ", " + a.getCity())
                        .collect(Collectors.toList()))
                .imageUrls(s.getAlbums().stream()
                        .map(ServiceAlbum::getImageUrl)
                        .collect(Collectors.toList()))
                .build();
    }
}