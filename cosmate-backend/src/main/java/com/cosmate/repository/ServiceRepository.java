package com.cosmate.repository;

import com.cosmate.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Integer> {
    List<Service> findByProviderIdAndStatus(Integer providerId, String status);
    List<Service> findByStatus(String status);
    List<Service> findByProviderId(Integer providerId);
    List<Service> findByServiceTypeAndStatus(String serviceType, String status);
}