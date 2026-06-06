package com.cosmate.service;

import com.cosmate.entity.CharacterRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CharacterRequestService {
    CharacterRequest create(CharacterRequest request, MultipartFile file, Integer currentUserId);
    List<CharacterRequest> getAll();
    CharacterRequest updateStatus(Integer id, String status);
}
