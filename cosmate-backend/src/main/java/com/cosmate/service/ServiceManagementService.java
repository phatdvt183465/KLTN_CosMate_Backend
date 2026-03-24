package com.cosmate.service;

import com.cosmate.dto.request.ServiceRequest;
import com.cosmate.dto.response.ServiceResponse;
import java.util.List;

public interface ServiceManagementService {
    List<ServiceResponse> getAllServices();
    List<ServiceResponse> getAllByProviderId(Integer providerId);
    ServiceResponse createService(ServiceRequest request);
    ServiceResponse updateService(Integer id, ServiceRequest request);
    List<ServiceResponse> getByProviderId(Integer providerId);
    ServiceResponse getById(Integer id);
    void deleteService(Integer id);
    List<ServiceResponse> getByServiceType(String serviceType);
}