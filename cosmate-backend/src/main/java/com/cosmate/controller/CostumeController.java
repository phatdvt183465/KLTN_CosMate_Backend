package com.cosmate.controller;

import com.cosmate.dto.request.CostumeRequest;
import com.cosmate.dto.response.CostumeResponse;
import com.cosmate.service.CostumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType; // Thêm cái này
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/costumes")
@RequiredArgsConstructor
public class CostumeController {

    private final CostumeService costumeService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CostumeResponse> create(@ModelAttribute CostumeRequest request) {
        return ResponseEntity.ok(costumeService.createCostume(request));
    }

    @GetMapping
    public ResponseEntity<List<CostumeResponse>> getAll() {
        return ResponseEntity.ok(costumeService.getAllCostumes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CostumeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(costumeService.getById(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CostumeResponse> update(@PathVariable Long id, @ModelAttribute CostumeRequest request) {
        return ResponseEntity.ok(costumeService.updateCostume(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        costumeService.deleteCostume(id);
        return ResponseEntity.noContent().build();
    }
}