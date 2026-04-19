package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.entity.Character;
import com.cosmate.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterRepository characterRepository;

    @GetMapping
    public ApiResponse<List<Character>> getAllCharacters() {
        return ApiResponse.<List<Character>>builder()
                .result(characterRepository.findAll())
                .message("Lấy danh sách nhân vật thành công!")
                .build();
    }
}