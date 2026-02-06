package com.cosmate.service.impl;

import com.cosmate.dto.request.EventRequest;
import com.cosmate.dto.response.EventResponse;
import com.cosmate.entity.Event;
import com.cosmate.repository.EventRepository;
import com.cosmate.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventServiceImpl implements EventService {

    @Autowired
    private EventRepository eventRepository;

    @Override
    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public EventResponse createEvent(EventRequest request) {
        Event event = new Event();
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setLocation(request.getLocation());
        event.setStartDate(request.getStartDate());
        event.setEndDate(request.getEndDate());
        event.setStatus(request.getStatus());
        event.setCreatedBy(request.getCreatedBy());

        return mapToResponse(eventRepository.save(event));
    }

    @Override
    public EventResponse getEventById(Integer id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Event: " + id));
        return mapToResponse(event);
    }

    private EventResponse mapToResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .status(event.getStatus())
                .createdBy(event.getCreatedBy())
                .build();
    }
}