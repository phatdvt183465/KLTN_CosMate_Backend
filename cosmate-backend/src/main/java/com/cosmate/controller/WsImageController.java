package com.cosmate.controller;

import com.cosmate.service.impl.TempImageStorage;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequiredArgsConstructor
@RequestMapping("/ws-image")
public class WsImageController {

    private final TempImageStorage storage;
    private final SimpMessagingTemplate ws;

    @PostMapping("/upload")
    public void upload(@RequestParam String sessionId,
                       @RequestPart MultipartFile file) throws Exception {

        String imageId = storage.save(file);

        ws.convertAndSend(
                "/topic/ws-image/" + sessionId,
                "/ws-image/view/" + imageId
        );
    }

    @GetMapping("/view/{id}")
    public ResponseEntity<Resource> view(@PathVariable String id) throws Exception {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body((Resource) storage.load(id));
    }
}
