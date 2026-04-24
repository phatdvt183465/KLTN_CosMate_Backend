package com.cosmate.service.impl;

import com.cosmate.entity.Notification;
import com.cosmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AsyncNotificationSender {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    private static final Logger logger = LoggerFactory.getLogger(AsyncNotificationSender.class);

    @Async("taskExecutor")
    public void sendNotificationEmail(Notification saved) {
        try {
            if ("CHAT_MESSAGE".equals(saved.getType())) return;

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
    }
}

