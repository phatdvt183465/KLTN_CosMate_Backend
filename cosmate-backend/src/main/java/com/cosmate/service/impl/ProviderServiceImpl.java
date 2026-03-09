package com.cosmate.service.impl;

import com.cosmate.dto.request.UpdateProviderRequest;
import com.cosmate.entity.Provider;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.repository.UserRepository;
import com.cosmate.service.ProviderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Provider createForUser(Integer userId) {
        Optional<Provider> existing = providerRepository.findByUserId(userId);
        if (existing.isPresent()) return existing.get();

        Provider p = Provider.builder()
                .userId(userId)
                .shopName(null)
                .shopAddressId(null)
                .avatarUrl(null)
                .bio(null)
                .bankAccountNumber(null)
                .bankName(null)
                .verified(false)
                .build();
        Provider saved = providerRepository.save(p);

        return saved;
    }

    @Override
    public Provider getByUserId(Integer userId) {
        return providerRepository.findByUserId(userId).orElseThrow(() -> new AppException(ErrorCode.PROVIDER_NOT_FOUND));
    }

    @Override
    public Provider getById(Integer providerId) {
        return providerRepository.findById(providerId).orElseThrow(() -> new AppException(ErrorCode.PROVIDER_NOT_FOUND));
    }

    @Override
    @Transactional
    public Provider updateOwnProvider(Integer userId, UpdateProviderRequest request) {
        Provider provider = providerRepository.findByUserId(userId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        if (request.getShopName() != null) provider.setShopName(request.getShopName());
        if (request.getShopAddressId() != null) provider.setShopAddressId(request.getShopAddressId());
        if (request.getBio() != null) provider.setBio(request.getBio());
        if (request.getBankAccountNumber() != null) provider.setBankAccountNumber(request.getBankAccountNumber());
        if (request.getBankName() != null) provider.setBankName(request.getBankName());
        // verified cannot be changed here
        return providerRepository.save(provider);
    }

    @Override
    @Transactional
    public Provider setVerified(Integer providerId, boolean verified) {
        Provider p = providerRepository.findById(providerId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        p.setVerified(verified);
        return providerRepository.save(p);
    }

    @Override
    @Transactional
    public Provider updateAvatarForUser(Integer userId, String avatarUrl) {
        Optional<Provider> opt = providerRepository.findByUserId(userId);
        if (opt.isEmpty()) return null;
        Provider p = opt.get();
        p.setAvatarUrl(avatarUrl);
        return providerRepository.save(p);
    }

    @Override
    @Transactional
    public Provider updateCoverImageForUser(Integer userId, String coverImageUrl) {
        Optional<Provider> opt = providerRepository.findByUserId(userId);
        if (opt.isEmpty()) return null;
        Provider p = opt.get();
        p.setCoverImageUrl(coverImageUrl);
        return providerRepository.save(p);
    }

    @Override
    public List<Provider> listAllProviders() {
        return providerRepository.findAll();
    }
}
