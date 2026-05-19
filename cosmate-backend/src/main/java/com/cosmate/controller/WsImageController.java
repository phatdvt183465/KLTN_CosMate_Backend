package com.cosmate.controller;

import com.cosmate.service.impl.TempImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource; // Import chuẩn của Spring
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

        // Hàm save mới trả về ID dạng UUID trần (Không kèm đuôi .jpg)
        String imageId = storage.save(file);

        // Bắn qua WebSocket cho Frontend Web hóng dữ liệu
        ws.convertAndSend(
                "/topic/ws-image/" + sessionId,
                "/ws-image/view/" + imageId
        );
    }

    @GetMapping("/view/{id}")
    public ResponseEntity<Resource> view(@PathVariable String id) {
        try {
            // Lấy Resource từ bộ nhớ tạm (Hàm load mới tự động bù đuôi .jpg thông minh)
            Resource resource = storage.load(id);

            // Kiểm tra file có thực sự tồn tại trên ổ cứng và đọc được không
            if (resource != null && resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            } else {
                // Nếu là ảnh cũ từ session trước đã bị xóa, trả về 404
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            // Đề phòng các lỗi hệ thống khác thì trả về 500 gọn gàng
            return ResponseEntity.internalServerError().build();
        }
    }
}