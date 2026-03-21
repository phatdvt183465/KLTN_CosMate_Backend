package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.NotificationResponse;
import com.cosmate.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        try {
            if (principal instanceof String) {
                String s = (String) principal;
                if (s.equalsIgnoreCase("anonymousUser")) return null;
                return Integer.valueOf(s);
            }
            if (principal instanceof Integer) return (Integer) principal;
            if (principal instanceof Long) return ((Long) principal).intValue();
            return Integer.valueOf(principal.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("")
    public ApiResponse<List<NotificationResponse>> all() {
        try {
            Integer uid = getCurrentUserId();
            if (uid == null) return ApiResponse.<List<NotificationResponse>>builder().code(401).message("Chưa xác thực").build();
            var list = notificationService.getAllForUser(uid);
            return ApiResponse.<List<NotificationResponse>>builder().result(list).code(0).build();
        } catch (Exception e) {
            return ApiResponse.<List<NotificationResponse>>builder().code(500).message(e.getMessage()).build();
        }
    }

    @PostMapping("/mark-read/{id}")
    public ApiResponse<Void> markRead(@PathVariable Integer id) {
        try {
            Integer uid = getCurrentUserId();
            if (uid == null) return ApiResponse.<Void>builder().code(401).message("Chưa xác thực").build();
            notificationService.markAsRead(id, uid);
            return ApiResponse.<Void>builder().code(0).build();
        } catch (Exception e) {
            return ApiResponse.<Void>builder().code(500).message(e.getMessage()).build();
        }
    }

    @PostMapping("/mark-all-read")
    public ApiResponse<Void> markAllRead() {
        try {
            Integer uid = getCurrentUserId();
            if (uid == null) return ApiResponse.<Void>builder().code(401).message("Chưa xác thực").build();
            notificationService.markAllAsRead(uid);
            return ApiResponse.<Void>builder().code(0).build();
        } catch (Exception e) {
            return ApiResponse.<Void>builder().code(500).message(e.getMessage()).build();
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        try {
            Integer uid = getCurrentUserId();
            if (uid == null) return ApiResponse.<Void>builder().code(401).message("Chưa xác thực").build();
            notificationService.delete(id, uid);
            return ApiResponse.<Void>builder().code(0).build();
        } catch (Exception e) {
            return ApiResponse.<Void>builder().code(500).message(e.getMessage()).build();
        }
    }
}

