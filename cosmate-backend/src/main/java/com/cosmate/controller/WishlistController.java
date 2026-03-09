package com.cosmate.controller;

import com.cosmate.dto.request.CreateWishlistRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.WishlistResponse;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;
    private static final Logger log = LoggerFactory.getLogger(WishlistController.class);

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        try {
            if (principal instanceof String) {
                String s = (String) principal;
                if (s.equalsIgnoreCase("anonymousUser")) return null;
                return Integer.valueOf(s);
            } else if (principal instanceof Integer) return (Integer) principal;
            else if (principal instanceof Long) return ((Long) principal).intValue();
            else return Integer.valueOf(principal.toString());
        } catch (Exception e) {
            log.debug("Unable to parse current principal to userId: {}", principal);
            return null;
        }
    }

    @PostMapping("")
    public ResponseEntity<ApiResponse<WishlistResponse>> create(@PathVariable("userId") Integer userId, @Validated @RequestBody CreateWishlistRequest request) {
        Integer currentUserId = getCurrentUserId();
        ApiResponse<WishlistResponse> api = new ApiResponse<>();
        if (currentUserId == null) {
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        if (!currentUserId.equals(userId)) {
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }

        try {
            WishlistResponse r = wishlistService.create(userId, request);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(r);
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error creating wishlist for user {}: {}", userId, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @GetMapping("")
    public ResponseEntity<ApiResponse<List<WishlistResponse>>> list(@PathVariable("userId") Integer userId) {
        Integer currentUserId = getCurrentUserId();
        ApiResponse<List<WishlistResponse>> api = new ApiResponse<>();
        if (currentUserId == null) {
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        if (!currentUserId.equals(userId)) {
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }

        List<WishlistResponse> list = wishlistService.listAllByUser(userId);
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(list);
        return ResponseEntity.ok(api);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WishlistResponse>> getById(@PathVariable("userId") Integer userId, @PathVariable("id") Integer id) {
        Integer currentUserId = getCurrentUserId();
        ApiResponse<WishlistResponse> api = new ApiResponse<>();
        if (currentUserId == null) {
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        if (!currentUserId.equals(userId)) {
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }

        try {
            WishlistResponse r = wishlistService.getById(userId, id);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(r);
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            if (ec == ErrorCode.WISHLIST_NOT_FOUND) return ResponseEntity.status(404).body(api);
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error getting wishlist {} for user {}: {}", id, userId, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("userId") Integer userId, @PathVariable("id") Integer id) {
        Integer currentUserId = getCurrentUserId();
        ApiResponse<Void> api = new ApiResponse<>();
        if (currentUserId == null) {
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        if (!currentUserId.equals(userId)) {
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }

        try {
            wishlistService.delete(userId, id);
            api.setCode(0);
            api.setMessage("OK");
            return ResponseEntity.ok(api);
        } catch (AppException ae) {
            ErrorCode ec = ae.getErrorCode();
            api.setCode(ec.getCode());
            api.setMessage(ec.getMessage());
            if (ec == ErrorCode.WISHLIST_NOT_FOUND) return ResponseEntity.status(404).body(api);
            if (ec == ErrorCode.FORBIDDEN) return ResponseEntity.status(403).body(api);
            return ResponseEntity.badRequest().body(api);
        } catch (Exception e) {
            log.error("Unexpected error deleting wishlist {} for user {}: {}", id, userId, e.getMessage(), e);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + e.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }
}
