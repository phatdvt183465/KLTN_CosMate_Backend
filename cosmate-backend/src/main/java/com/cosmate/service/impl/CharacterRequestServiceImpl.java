package com.cosmate.service.impl;

import com.cosmate.entity.CharacterRequest;
import com.cosmate.repository.CharacterRequestRepository;
import com.cosmate.service.CharacterRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CharacterRequestServiceImpl implements CharacterRequestService {

    private final CharacterRequestRepository characterRequestRepository;

    @Override
    public CharacterRequest create(CharacterRequest request, Integer currentUserId) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new IllegalArgumentException("providerId/currentUserId không hợp lệ");
        }
        request.setId(null);
        request.setProviderId(currentUserId);
        request.setStatus("PENDING");
        return characterRequestRepository.save(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CharacterRequest> getAll() {
        return characterRequestRepository.findAll();
    }

    @Override
    public CharacterRequest updateStatus(Integer id, String status) {
        CharacterRequest request = characterRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Character request not found"));
        request.setStatus(status == null ? null : status.toUpperCase());
        return characterRequestRepository.save(request);
    }
}
