package com.cosmate.service.impl;

import com.cosmate.dto.response.NotificationResponse;
import com.cosmate.entity.Notification;
import com.cosmate.entity.User;
import com.cosmate.repository.NotificationRepository;
import com.cosmate.repository.UserRepository;
import com.cosmate.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AsyncNotificationSender asyncNotificationSender;

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Override
    public Notification create(Notification n) {
        if (n.getSendAt() == null) n.setSendAt(LocalDateTime.now());
        if (n.getIsRead() == null) n.setIsRead(false);
        Notification saved = notificationRepository.save(n);

        // broadcast to websocket topic for the user
        if (saved.getUser() != null && saved.getUser().getId() != null) {
            NotificationResponse resp = toDto(saved);
            messagingTemplate.convertAndSend("/topic/notifications/" + saved.getUser().getId(), resp);
        }
        // send email asynchronously so callers don't block on email delivery
        try {
            asyncNotificationSender.sendNotificationEmail(saved);
        } catch (Exception ex) {
            // in rare cases async invocation could throw; log but don't fail notification creation
            logger.error("Error while scheduling async notification email for id={}", saved.getId(), ex);
        }

        return saved;
    }

    @Override
    public List<NotificationResponse> getAllForUser(Integer userId) throws Exception {
        User u = userRepository.findById(userId).orElseThrow(() -> new Exception("User not found"));
        return notificationRepository.findAllByUserOrderBySendAtDesc(u).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public void markAsRead(Integer notificationId, Integer userId) throws Exception {
        Notification n = notificationRepository.findById(notificationId).orElseThrow(() -> new Exception("Notification not found"));
        if (n.getUser() == null || !n.getUser().getId().equals(userId)) throw new Exception("Not allowed");
        n.setIsRead(true);
        notificationRepository.save(n);
    }

    @Override
    public void markAllAsRead(Integer userId) throws Exception {
        User u = userRepository.findById(userId).orElseThrow(() -> new Exception("User not found"));
        List<Notification> all = notificationRepository.findAllByUserOrderBySendAtDesc(u);
        for (Notification n : all) {
            n.setIsRead(true);
        }
        notificationRepository.saveAll(all);
    }

    @Override
    public void delete(Integer notificationId, Integer userId) throws Exception {
        Notification n = notificationRepository.findById(notificationId).orElseThrow(() -> new Exception("Notification not found"));
        if (n.getUser() == null || !n.getUser().getId().equals(userId)) throw new Exception("Not allowed");
        notificationRepository.delete(n);
    }

    private NotificationResponse toDto(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUser() == null ? null : n.getUser().getId())
                .type(n.getType())
                .header(n.getHeader())
                .content(n.getContent())
                .sendAt(n.getSendAt())
                .isRead(n.getIsRead())
                .build();
    }
}

