package com.cosmate.service;

import com.cosmate.dto.request.EventRequest;
import com.cosmate.dto.request.JoinEventRequest;
import com.cosmate.dto.request.VoteRequest;
import com.cosmate.dto.response.EventResponse;
import java.util.List;

public interface EventService {
    List<EventResponse> getAllEvents();
    EventResponse createEvent(EventRequest request);
    EventResponse getEventById(Integer id);
    void joinEvent(JoinEventRequest request);
    void voteForParticipant(VoteRequest request);
}