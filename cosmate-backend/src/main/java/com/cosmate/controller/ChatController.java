package com.cosmate.controller;

import com.cosmate.dto.request.ChatMessageRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.ChatMessageResponse;
import com.cosmate.entity.ChatRoom;
import com.cosmate.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // API REST: Lấy/Tạo phòng chat (Bọc trong ApiResponse)
    @GetMapping("/room")
    public ApiResponse<ChatRoom> getOrCreateRoom(
            @RequestParam Integer user1Id,
            @RequestParam Integer user2Id) {

        return ApiResponse.<ChatRoom>builder()
                .result(chatService.getOrCreateRoom(user1Id, user2Id))
                .message("Lấy thông tin phòng chat thành công")
                .build();
    }

    // THÊM API LẤY LỊCH SỬ TIN NHẮN
    @GetMapping("/messages/{roomId}")
    public ApiResponse<List<ChatMessageResponse>> getMessageHistory(@PathVariable Integer roomId) {
        return ApiResponse.<List<ChatMessageResponse>>builder()
                .result(chatService.getMessageHistory(roomId))
                .message("Lấy lịch sử tin nhắn thành công")
                .build();
    }

    @MessageMapping("/chat.sendMessage")
    public void processMessage(@Payload ChatMessageRequest request) {
        chatService.saveMessageAndBroadcast(request);
    }
}