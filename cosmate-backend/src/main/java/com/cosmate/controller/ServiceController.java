package com.cosmate.controller;

import com.cosmate.dto.request.ServiceRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.ServiceResponse;
import com.cosmate.service.ServiceManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceManagementService serviceManagementService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ServiceResponse> create(@ModelAttribute ServiceRequest request) {
        return ApiResponse.<ServiceResponse>builder()
                .result(serviceManagementService.createService(request))
                .message("Tạo dịch vụ thành công!")
                .build();
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ServiceResponse> update(@PathVariable Integer id, @ModelAttribute ServiceRequest request) {
        return ApiResponse.<ServiceResponse>builder()
                .result(serviceManagementService.updateService(id, request))
                .message("Cập nhật dịch vụ thành công!")
                .build();
    }

    @GetMapping("/provider/{providerId}")
    public ApiResponse<List<ServiceResponse>> getByProvider(@PathVariable Integer providerId) {
        return ApiResponse.<List<ServiceResponse>>builder()
                .result(serviceManagementService.getByProviderId(providerId))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<ServiceResponse> getById(@PathVariable Integer id) {
        return ApiResponse.<ServiceResponse>builder()
                .result(serviceManagementService.getById(id))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        serviceManagementService.deleteService(id);
        return ApiResponse.<Void>builder()
                .message("Đã xóa dịch vụ!")
                .build();
    }
}