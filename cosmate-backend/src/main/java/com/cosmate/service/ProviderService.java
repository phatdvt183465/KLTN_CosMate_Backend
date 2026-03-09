package com.cosmate.service;

import com.cosmate.dto.request.UpdateProviderRequest;
import com.cosmate.entity.Provider;

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
}
