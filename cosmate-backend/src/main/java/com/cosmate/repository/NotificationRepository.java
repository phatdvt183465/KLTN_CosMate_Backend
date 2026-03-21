package com.cosmate.repository;

import com.cosmate.entity.Notification;
import com.cosmate.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findAllByUserOrderBySendAtDesc(User user);
}

