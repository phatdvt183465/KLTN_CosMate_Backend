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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ServiceManagementServiceImpl implements ServiceManagementService {

    private final ServiceRepository serviceRepository;
    private final ObjectMapper objectMapper;
    private final FirebaseConfig firebaseConfig; // Inject config để lấy bucket chuẩn

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
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));

        if (request.getName() != null) service.setName(request.getName());
        if (request.getDescription() != null) service.setDescription(request.getDescription());
        if (request.getPrice() != null) service.setPrice(request.getPrice());

        // Update Areas if provided
        if (request.getAreas() != null && !request.getAreas().isEmpty()) {
            service.getAreas().clear();
            handleAreas(service, request.getAreas());
        }

        // Update Albums if new files provided
        if (request.getAlbumFiles() != null && !request.getAlbumFiles().isEmpty()) {
            service.getAlbums().clear();
            handleAlbums(service, request.getAlbumFiles());
        }

        return mapToResponse(serviceRepository.save(service));
    }

    @Override
    public List<ServiceResponse> getByProviderId(Integer providerId) {
        return serviceRepository.findByProviderId(providerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ServiceResponse getById(Integer id) {
        return mapToResponse(serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found")));
    }

    @Override
    @Transactional
    public void deleteService(Integer id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));
        serviceRepository.delete(service);
    }

    // --- Helpers ---

    private void handleAreas(Service service, String json) {
        if (json == null || json.isEmpty()) return;
        try {
            List<String> areaNames = objectMapper.readValue(json, new TypeReference<>() {});
            for (String name : areaNames) {
                ServiceArea area = new ServiceArea();
                area.setAreaName(name);
                area.setService(service);
                service.getAreas().add(area);
            }
        } catch (IOException e) { throw new RuntimeException("Area JSON error"); }
    }

    private void handleAlbums(Service service, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;
        Bucket bucket = firebaseConfig.getBucket(); // Lấy bucket từ config
        if (bucket == null) throw new RuntimeException("Firebase Bucket not initialized");

        for (MultipartFile file : files) {
            try {
                String fileName = "services/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
                bucket.create(fileName, file.getBytes(), file.getContentType());

                ServiceAlbum album = new ServiceAlbum();
                album.setImageUrl("https://storage.googleapis.com/" + bucket.getName() + "/" + fileName);
                album.setService(service);
                service.getAlbums().add(album);
            } catch (IOException e) { throw new RuntimeException("Firebase Upload Error"); }
        }
    }

    private void mapBaseInfo(Service s, ServiceRequest r) {
        s.setName(r.getName());
        s.setDescription(r.getDescription());
        s.setPrice(r.getPrice());
        s.setProviderId(r.getProviderId());
    }

    private ServiceResponse mapToResponse(Service s) {
        return ServiceResponse.builder()
                .id(s.getId()).name(s.getName()).description(s.getDescription())
                .price(s.getPrice()).status(s.getStatus()).providerId(s.getProviderId())
                .areas(s.getAreas().stream().map(ServiceArea::getAreaName).collect(Collectors.toList()))
                .imageUrls(s.getAlbums().stream().map(ServiceAlbum::getImageUrl).collect(Collectors.toList()))
                .build();
    }
}