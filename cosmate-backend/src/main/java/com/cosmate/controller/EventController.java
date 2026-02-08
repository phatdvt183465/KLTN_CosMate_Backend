package com.cosmate.controller;

import com.cosmate.dto.request.EventRequest;
import com.cosmate.dto.response.EventResponse;
import com.cosmate.entity.Event;
import com.cosmate.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private EventService eventService;

    @GetMapping
    public ResponseEntity<?> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.createEvent(request));
    }
}