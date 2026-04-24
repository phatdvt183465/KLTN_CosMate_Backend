package com.cosmate.controller;

import com.cosmate.dto.request.ChangePasswordRequest;
import com.cosmate.dto.request.UpdateProfileRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.UserListItem;
import com.cosmate.dto.response.UserResponse;
import com.cosmate.entity.User;
import com.cosmate.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import java.io.InputStream;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        try {
            // common: principal is a String userId
            if (principal instanceof String) {
                String s = (String) principal;
                if (s.equalsIgnoreCase("anonymousUser")) return null;
                return Integer.valueOf(s);
            }
            // sometimes frameworks set principal as Integer/Long
            if (principal instanceof Integer) return (Integer) principal;
            if (principal instanceof Long) return ((Long) principal).intValue();
            // fallback toString
            return Integer.valueOf(principal.toString());
        } catch (Exception e) {
            log.debug("Unable to parse current principal to userId: {}", principal);
            return null;
        }
    }

    // Simplified update endpoints
    // 1) JSON-only update (no avatar)
    @PutMapping(value = "/{id}/profile", consumes = { MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<ApiResponse<UserResponse>> updateProfileJson(@PathVariable("id") Integer id,
                                                                       @RequestBody UpdateProfileRequest request) {
        log.debug("updateProfileJson called for id={} request={}", id, request);
        return handleUpdateProfile(id, request, null);
    }

    // New: dedicated avatar upload endpoint (multipart/form-data with single 'avatar' file) using PUT
    @PutMapping(value = "/{id}/avatar", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ApiResponse<UserResponse>> updateAvatar(@PathVariable("id") Integer id,
                                                                   @RequestPart(value = "avatar") MultipartFile avatar) {
        log.debug("updateAvatar called for id={} avatarPresent={}", id, avatar != null && !avatar.isEmpty());

        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            ApiResponse api = new ApiResponse();
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        if (!currentUserId.equals(id)) {
            ApiResponse api = new ApiResponse();
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }

        User user;
        try {
            user = userService.updateAvatar(id, avatar);
        } catch (com.cosmate.exception.AppException ae) {
            com.cosmate.exception.ErrorCode ec = ae.getErrorCode();
            ApiResponse api = new ApiResponse();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            if (ec == com.cosmate.exception.ErrorCode.FORBIDDEN || ec == com.cosmate.exception.ErrorCode.ACCOUNT_BANNED) {
                return ResponseEntity.status(403).body(api);
            }
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error in updateAvatar: {}", e.getMessage(), e);
            ApiResponse api = new ApiResponse();
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }

        UserResponse resp = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole() == null ? null : user.getRole().getRoleName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .numberOfToken(user.getNumberOfToken())
                .build();

        ApiResponse<UserResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    // Shared helper that performs auth checks, delegates to service, and builds response
    private ResponseEntity<ApiResponse<UserResponse>> handleUpdateProfile(Integer id, UpdateProfileRequest request, MultipartFile avatar) {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            ApiResponse api = new ApiResponse();
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        if (!currentUserId.equals(id)) {
            log.warn("Unauthorized profile update attempt: currentUserId={} pathId={}", currentUserId, id);
            ApiResponse api = new ApiResponse();
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }

        User user;
        try {
            user = userService.updateProfile(id, request, avatar);
        } catch (com.cosmate.exception.AppException ae) {
            com.cosmate.exception.ErrorCode ec = ae.getErrorCode();
            log.warn("AppException during updateProfile: code={} message={}", ec.getCode(), ec.getMessage(), ae);
            ApiResponse api = new ApiResponse();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            if (ec == com.cosmate.exception.ErrorCode.FORBIDDEN || ec == com.cosmate.exception.ErrorCode.ACCOUNT_BANNED) {
                return ResponseEntity.status(403).body(api);
            }
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error in updateProfile: {}", e.getMessage(), e);
            ApiResponse api = new ApiResponse();
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }

        UserResponse resp = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole() == null ? null : user.getRole().getRoleName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .numberOfToken(user.getNumberOfToken())
                .build();

        log.debug("Profile updated successfully for user id={} avatarUrl={}", user.getId(), user.getAvatarUrl());

        ApiResponse<UserResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<UserResponse>> viewProfile(@PathVariable("id") Integer id) {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            ApiResponse api = new ApiResponse();
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        boolean allowed = false;
        if (currentUserId.equals(id)) allowed = true;
        else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                var authorities = auth.getAuthorities();
                allowed = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority()));
            }
        }
        if (!allowed) {
            ApiResponse api = new ApiResponse();
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }

        User user = userService.getById(id);
        UserResponse resp = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole() == null ? null : user.getRole().getRoleName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .numberOfToken(user.getNumberOfToken())
                .build();

        ApiResponse<UserResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    @PostMapping("/{id}/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@PathVariable("id") Integer id, @Validated @RequestBody ChangePasswordRequest request) {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            ApiResponse api = new ApiResponse();
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        if (!currentUserId.equals(id)) {
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

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserListItem>>> searchUsers(@RequestParam String keyword) {
        List<UserListItem> list = userService.searchUsers(keyword);
        ApiResponse<List<UserListItem>> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(list);
        return ResponseEntity.ok(api);
    }

    @PostMapping(value = "/debug/echo-multipart", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ApiResponse<Map<String, Object>>> debugEchoMultipart(MultipartHttpServletRequest req) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            // parameters
            Map<String, String[]> params = req.getParameterMap();
            result.put("params", params);

            // files and parts
            Map<String, Object> filesInfo = new java.util.HashMap<>();
            java.util.Iterator<String> it = req.getFileNames();
            while (it != null && it.hasNext()) {
                String name = it.next();
                List<MultipartFile> files = req.getFiles(name);
                List<Object> infos = new java.util.ArrayList<>();
                for (MultipartFile f : files) {
                    Map<String, Object> info = new java.util.HashMap<>();
                    info.put("name", f.getName());
                    info.put("originalFilename", f.getOriginalFilename());
                    info.put("contentType", f.getContentType());
                    info.put("size", f.getSize());
                    // include small text content
                    if (f.getSize() > 0 && f.getSize() <= 16 * 1024 && (f.getContentType() == null || !f.getContentType().toLowerCase().startsWith("image/"))) {
                        try {
                            String txt = new String(f.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                            info.put("textPreview", txt);
                        } catch (Exception e) {
                            info.put("textPreviewError", e.getMessage());
                        }
                    }
                    infos.add(info);
                }
                filesInfo.put(name, infos);
            }
            result.put("files", filesInfo);

            // raw parts fallback
            try {
                List<Object> rawParts = new java.util.ArrayList<>();
                for (Part p : req.getParts()) {
                    Map<String, Object> pi = new java.util.HashMap<>();
                    pi.put("name", p.getName());
                    pi.put("contentType", p.getContentType());
                    pi.put("size", p.getSize());
                    if (p.getSize() > 0 && p.getSize() <= 16 * 1024) {
                        try (InputStream is = p.getInputStream()) {
                            byte[] b = is.readAllBytes();
                            pi.put("textPreview", new String(b, java.nio.charset.StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            pi.put("textPreviewError", e.getMessage());
                        }
                    }
                    rawParts.add(pi);
                }
                result.put("rawParts", rawParts);
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            ApiResponse<Map<String, Object>> api = new ApiResponse<>();
            api.setCode(1002);
            api.setMessage("Failed to inspect multipart request: " + e.getMessage());
            return ResponseEntity.internalServerError().body(api);
        }

        ApiResponse<Map<String, Object>> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(result);
        return ResponseEntity.ok(api);
    }
}
