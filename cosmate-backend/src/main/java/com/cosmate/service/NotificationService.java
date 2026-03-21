package com.cosmate.service;

import com.cosmate.dto.response.NotificationResponse;
import com.cosmate.entity.Notification;
import com.cosmate.entity.User;

import java.util.List;

public interface NotificationService {
    Notification create(Notification n);
    List<NotificationResponse> getAllForUser(Integer userId) throws Exception;
    void markAsRead(Integer notificationId, Integer userId) throws Exception;
    void markAllAsRead(Integer userId) throws Exception;
    void delete(Integer notificationId, Integer userId) throws Exception;
}

