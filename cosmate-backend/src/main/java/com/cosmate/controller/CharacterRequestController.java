package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.entity.CharacterRequest;
import com.cosmate.service.CharacterRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/character-requests")
@RequiredArgsConstructor
public class CharacterRequestController {

    private final CharacterRequestService characterRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CharacterRequest> create(@RequestBody CharacterRequest request, @RequestParam(required = false) Integer providerId) {
        return ApiResponse.<CharacterRequest>builder()
                .message("Gửi yêu cầu thêm nhân vật thành công!")
                .result(characterRequestService.create(request, providerId))
                .build();
    }

    @GetMapping
    public ApiResponse<List<CharacterRequest>> getAll() {
        return ApiResponse.<List<CharacterRequest>>builder()
                .message("Lấy danh sách yêu cầu nhân vật thành công!")
                .result(characterRequestService.getAll())
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<CharacterRequest> updateStatus(@PathVariable Integer id, @RequestParam String status) {
        return ApiResponse.<CharacterRequest>builder()
                .message("Cập nhật trạng thái yêu cầu thành công!")
                .result(characterRequestService.updateStatus(id, status))
                .build();
    }
}
