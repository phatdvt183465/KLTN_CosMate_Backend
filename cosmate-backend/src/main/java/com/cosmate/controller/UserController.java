package com.cosmate.controller;

import com.cosmate.dto.request.ChangePasswordRequest;
import com.cosmate.dto.request.UpdateProfileRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.UserListItem;
import com.cosmate.entity.User;
import com.cosmate.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        try {
            return Integer.valueOf((String) auth.getPrincipal());
        } catch (Exception e) {
            return null;
        }
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<UserListItem>> updateProfile(@PathVariable("id") Integer id, @Validated @RequestBody UpdateProfileRequest request) {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(id)) {
            // only owner can update profile
            ApiResponse api = new ApiResponse();
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }

        User user = userService.updateProfile(id, request);
        UserListItem item = UserListItem.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .build();

        ApiResponse<UserListItem> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(item);
        return ResponseEntity.ok(api);
    }

    @PostMapping("/{id}/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@PathVariable("id") Integer id, @Validated @RequestBody ChangePasswordRequest request) {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(id)) {
            ApiResponse api = new ApiResponse();
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }
        userService.changePassword(id, request);
        ApiResponse<Void> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        return ResponseEntity.ok(api);
    }

    @PostMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<Void>> lockUser(@PathVariable("id") Integer id) {
        userService.lockUser(id);
        ApiResponse<Void> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        return ResponseEntity.ok(api);
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable("id") Integer id) {
        userService.unlockUser(id);
        ApiResponse<Void> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        return ResponseEntity.ok(api);
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<ApiResponse<Void>> banUser(@PathVariable("id") Integer id) {
        userService.banUser(id);
        ApiResponse<Void> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        return ResponseEntity.ok(api);
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<ApiResponse<Void>> unbanUser(@PathVariable("id") Integer id) {
        userService.unbanUser(id);
        ApiResponse<Void> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        return ResponseEntity.ok(api);
    }

    @GetMapping("")
    public ResponseEntity<ApiResponse<List<UserListItem>>> listUsers() {
        List<UserListItem> list = userService.listUsers();
        ApiResponse<List<UserListItem>> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(list);
        return ResponseEntity.ok(api);
    }
}
