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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final JavaMailSender mailSender;

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
        // Send email if user has email
        try {
            if (saved.getUser() != null && saved.getUser().getId() != null) {
                Optional<com.cosmate.entity.User> uOpt = userRepository.findById(saved.getUser().getId());
                if (uOpt.isPresent()) {
                    com.cosmate.entity.User u = uOpt.get();
                    String to = u.getEmail();
                    if (to != null && !to.isBlank()) {
                        SimpleMailMessage msg = new SimpleMailMessage();
                        msg.setTo(to);
                        msg.setSubject(saved.getHeader() == null ? "Thông báo từ CosMate" : saved.getHeader());
                        String body = saved.getContent() == null ? "" : saved.getContent();
                        body += "\n\nThời gian: " + (saved.getSendAt() == null ? "" : saved.getSendAt().toString());
                        msg.setText(body);
                        try {
                            mailSender.send(msg);
                            logger.info("Sent notification email to {} for notification id={}", to, saved.getId());
                        } catch (Exception ex) {
                            logger.error("Failed to send notification email to {} for notification id={}", to, saved.getId(), ex);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Error while attempting to send notification email for id={}", saved.getId(), ex);
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

