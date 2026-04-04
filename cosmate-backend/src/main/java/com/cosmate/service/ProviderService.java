package com.cosmate.service;

import com.cosmate.dto.request.UpdateProviderRequest;
import com.cosmate.dto.response.ProviderResponse;
import com.cosmate.entity.Provider;
import com.cosmate.dto.response.ProviderPublicResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProviderService {
    Provider createForUser(Integer userId);
    Provider getByUserId(Integer userId);
    Provider getById(Integer providerId);
    Provider updateOwnProvider(Integer userId, UpdateProviderRequest request);
    Provider setVerified(Integer providerId, boolean verified);
    Provider updateAvatarForUser(Integer userId, String avatarUrl);
    Provider updateCoverImageForUser(Integer userId, String coverImageUrl);
    List<Provider> listAllProviders();
    List<ProviderResponse> getProvidersByRole(String roleName);

    // convenience methods that return response DTOs or perform uploads so controllers stay thin
    List<ProviderPublicResponse> listAllProvidersPublic();
    ProviderResponse getResponseByProviderId(Integer providerId, boolean includeBankInfo);
    ProviderResponse getResponseByUserId(Integer userId, boolean includeBankInfo);
    ProviderResponse updateCoverImageForUserUpload(Integer userId, MultipartFile coverImage) throws Exception;
}
