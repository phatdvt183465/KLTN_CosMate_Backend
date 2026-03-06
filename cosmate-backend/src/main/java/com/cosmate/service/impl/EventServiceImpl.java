package com.cosmate.service.impl;

import com.cosmate.configuration.FirebaseConfig;
import com.cosmate.dto.request.EventRequest;
import com.cosmate.dto.request.JoinEventRequest;
import com.cosmate.dto.request.VoteRequest;
import com.cosmate.dto.response.EventResponse;
import com.cosmate.entity.Event;
import com.cosmate.entity.EventParticipant;
import com.cosmate.entity.Vote;
import com.cosmate.repository.EventParticipantRepository;
import com.cosmate.repository.EventRepository;
import com.cosmate.repository.VoteRepository;
import com.cosmate.service.AIService;
import com.cosmate.service.EventService;
import com.cosmate.service.FirebaseStorageService;
import com.google.cloud.storage.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor // Dùng lombok để tự động Autowired cho gọn
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository participantRepository;
    private final VoteRepository voteRepository;
    private final AIService aiService;
    private final FirebaseConfig firebaseConfig;
    private final FirebaseStorageService firebaseStorageService;

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
        event.setStartDate(request.getStartDate());
        event.setEndDate(request.getEndDate());
        String status = request.getStatus();
        if (status == null || status.isBlank()) {
            status = "PENDING_APPROVAL";
        }
        event.setStatus(status);
        event.setCreatedBy(request.getCreatedBy());

        return mapToResponse(eventRepository.save(event));
    }

    @Override
    public EventResponse getEventById(Integer id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Event: " + id));
        return mapToResponse(event);
    }

    // ==========================================
    // LOGIC THAM GIA SỰ KIỆN VÀ BÌNH CHỌN
    // ==========================================

    @Override
    @Transactional
    public void joinEvent(JoinEventRequest request) {
        // 1. Kiểm tra xem người này đã nộp bài cho event này chưa
        if (participantRepository.existsByEventIdAndCosplayerId(request.getEventId(), request.getCosplayerId())) {
            throw new RuntimeException("Bạn đã tham gia sự kiện này rồi, không được nộp bài lần 2!");
        }

        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sự kiện!"));

        MultipartFile file = request.getSubmissionImage();
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Vui lòng đính kèm ảnh dự thi!");
        }

        // 2. Gọi AI kiểm tra ảnh 18+ / bạo lực
        aiService.validateImageContent(file);

        // 3. UPLOAD FIREBASE
        String original = file.getOriginalFilename();
        // Làm sạch tên file (xóa ký tự tiếng Việt, dấu cách...) để URL không bị lỗi
        String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Build đường dẫn: events/{eventId}/participants/{timestamp}_{safeName}
        String path = String.format("events/%d/participants/%d_%s", event.getId(), System.currentTimeMillis(), safeName);

        // Gọi thẳng hàm sẽ trả về cái link có thể xem public
        String imageUrl = firebaseStorageService.uploadFile(file, path);

        // 4. Lưu bài dự thi vào Database
        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setCosplayerId(request.getCosplayerId());
        participant.setSubmissionImageUrl(imageUrl);

        participantRepository.save(participant);
    }

    @Override
    @Transactional
    public void voteForParticipant(VoteRequest request) {
        // 1. Kiểm tra user này đã vote cho bài thi này chưa
        if (voteRepository.existsByParticipantIdAndVoterId(request.getParticipantId(), request.getVoterId())) {
            throw new RuntimeException("Bạn đã bình chọn cho bài dự thi này rồi!");
        }

        EventParticipant participant = participantRepository.findById(request.getParticipantId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài dự thi!"));

        // 2. Lưu lượt vote
        Vote vote = new Vote();
        vote.setEventId(request.getEventId());
        vote.setVoterId(request.getVoterId());
        vote.setParticipant(participant);
        vote.setScore(1); // 1 lượt bấm = 1 điểm

        voteRepository.save(vote);
    }

    private EventResponse mapToResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .status(event.getStatus())
                .createdBy(event.getCreatedBy())
                .build();
    }
}