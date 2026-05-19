package com.cosmate.service;

import com.cosmate.entity.CharacterRequest;

import java.util.List;

public interface CharacterRequestService {
    CharacterRequest create(CharacterRequest request, Integer currentUserId);
    List<CharacterRequest> getAll();
    CharacterRequest updateStatus(Integer id, String status);
}
