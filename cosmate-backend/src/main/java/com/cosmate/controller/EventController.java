package com.cosmate.controller;

import com.cosmate.dto.request.EventRequest;
import com.cosmate.dto.request.JoinEventRequest;
import com.cosmate.dto.request.VoteRequest;
import com.cosmate.dto.response.EventResponse;
import com.cosmate.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<?> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.createEvent(request));
    }

    // --- API Tham gia sự kiện (Nhận Text + Ảnh) ---
    @PostMapping(value = "/join", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> joinEvent(@ModelAttribute JoinEventRequest request) {
        eventService.joinEvent(request);
        return ResponseEntity.ok("Nộp bài dự thi thành công!");
    }

    // --- API Bấm Bình chọn (Chỉ nhận Text/JSON) ---
    @PostMapping("/vote")
    public ResponseEntity<?> vote(@RequestBody VoteRequest request) {
        eventService.voteForParticipant(request);
        return ResponseEntity.ok("Bình chọn thành công!");
    }
}