package com.cosmate.controller;

import com.cosmate.dto.request.WithdrawRequestCreate;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.WithdrawRequestResponse;
import com.cosmate.entity.User;
import com.cosmate.entity.WithdrawRequest;
import com.cosmate.service.UserService;
import com.cosmate.service.WithdrawRequestService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/withdraws")
@RequiredArgsConstructor
public class WithdrawRequestController {

    private static final Logger log = LoggerFactory.getLogger(WithdrawRequestController.class);

    private final WithdrawRequestService withdrawRequestService;
    private final UserService userService;

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
            log.debug("Unable to parse current principal to userId: {}", principal);
            return null;
        }
    }

    @PostMapping("")
    public ResponseEntity<ApiResponse<WithdrawRequestResponse>> create(@RequestBody WithdrawRequestCreate req) {
        ApiResponse<WithdrawRequestResponse> api = new ApiResponse<>();
        Integer uid = getCurrentUserId();
        if (uid == null) {
            api.setCode(1001); api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }

        User user = userService.getById(uid);
        try {
            WithdrawRequest wr = withdrawRequestService.createWithdrawRequest(user, req);
            WithdrawRequestResponse resp = WithdrawRequestResponse.builder()
                    .id(wr.getId())
                    .userId(user.getId())
                    .walletId(wr.getWallet().getWalletId())
                    .amount(wr.getAmount())
                    .bankAccountNumber(wr.getBankAccountNumber())
                    .bankName(wr.getBankName())
                    .status(wr.getStatus())
                    .requestedAt(wr.getRequestedAt())
                    .build();
            api.setCode(0); api.setMessage("OK"); api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            api.setCode(1004); api.setMessage(e.getMessage());
            return ResponseEntity.ok(api);
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<List<WithdrawRequestResponse>>> getRequestsForUser(@PathVariable Integer userId) {
        ApiResponse<List<WithdrawRequestResponse>> api = new ApiResponse<>();
        Integer uid = getCurrentUserId();
        if (uid == null) { api.setCode(1001); api.setMessage("Chưa xác thực - Vui lòng đăng nhập"); return ResponseEntity.status(401).body(api); }

        boolean allowed = false;
        if (uid.equals(userId)) allowed = true;
        else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                var authorities = auth.getAuthorities();
                allowed = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority()));
            }
        }

        if (!allowed) { api.setCode(1006); api.setMessage("Không có quyền thực hiện thao tác này!"); return ResponseEntity.status(403).body(api); }

        User user = userService.getById(userId);
        List<WithdrawRequest> list = withdrawRequestService.getRequestsForUser(user);
        List<WithdrawRequestResponse> resp = list.stream().map(wr -> WithdrawRequestResponse.builder()
                .id(wr.getId())
                .userId(wr.getUser().getId())
                .walletId(wr.getWallet().getWalletId())
                .amount(wr.getAmount())
                .bankAccountNumber(wr.getBankAccountNumber())
                .bankName(wr.getBankName())
                .status(wr.getStatus())
                .requestedAt(wr.getRequestedAt())
                .build()).collect(Collectors.toList());
        api.setCode(0); api.setMessage("OK"); api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    @GetMapping("")
    public ResponseEntity<ApiResponse<List<WithdrawRequestResponse>>> listAll() {
        ApiResponse<List<WithdrawRequestResponse>> api = new ApiResponse<>();
        Integer uid = getCurrentUserId();
        if (uid == null) { api.setCode(1001); api.setMessage("Chưa xác thực - Vui lòng đăng nhập"); return ResponseEntity.status(401).body(api); }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = false;
        if (auth != null && auth.isAuthenticated()) {
            var authorities = auth.getAuthorities();
            allowed = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority()));
        }
        if (!allowed) { api.setCode(1006); api.setMessage("Không có quyền thực hiện thao tác này!"); return ResponseEntity.status(403).body(api); }

        List<WithdrawRequest> list = withdrawRequestService.getAllRequests();
        List<WithdrawRequestResponse> resp = list.stream().map(wr -> WithdrawRequestResponse.builder()
                .id(wr.getId())
                .userId(wr.getUser().getId())
                .walletId(wr.getWallet().getWalletId())
                .amount(wr.getAmount())
                .bankAccountNumber(wr.getBankAccountNumber())
                .bankName(wr.getBankName())
                .status(wr.getStatus())
                .requestedAt(wr.getRequestedAt())
                .build()).collect(Collectors.toList());
        api.setCode(0); api.setMessage("OK"); api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<WithdrawRequestResponse>> approve(@PathVariable Integer id) {
        ApiResponse<WithdrawRequestResponse> api = new ApiResponse<>();
        Integer uid = getCurrentUserId();
        if (uid == null) { api.setCode(1001); api.setMessage("Chưa xác thực - Vui lòng đăng nhập"); return ResponseEntity.status(401).body(api); }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = false;
        if (auth != null && auth.isAuthenticated()) {
            var authorities = auth.getAuthorities();
            allowed = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority()));
        }
        if (!allowed) { api.setCode(1006); api.setMessage("Không có quyền thực hiện thao tác này!"); return ResponseEntity.status(403).body(api); }

        User admin = userService.getById(uid);
        try {
            WithdrawRequest wr = withdrawRequestService.approveRequest(id, admin);
            WithdrawRequestResponse resp = WithdrawRequestResponse.builder()
                    .id(wr.getId())
                    .userId(wr.getUser().getId())
                    .walletId(wr.getWallet().getWalletId())
                    .amount(wr.getAmount())
                    .bankAccountNumber(wr.getBankAccountNumber())
                    .bankName(wr.getBankName())
                    .status(wr.getStatus())
                    .requestedAt(wr.getRequestedAt())
                    .build();
            api.setCode(0); api.setMessage("OK"); api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            api.setCode(1004); api.setMessage(e.getMessage()); return ResponseEntity.ok(api);
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<WithdrawRequestResponse>> reject(@PathVariable Integer id, @RequestParam(required = false) String reason) {
        ApiResponse<WithdrawRequestResponse> api = new ApiResponse<>();
        Integer uid = getCurrentUserId();
        if (uid == null) { api.setCode(1001); api.setMessage("Chưa xác thực - Vui lòng đăng nhập"); return ResponseEntity.status(401).body(api); }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = false;
        if (auth != null && auth.isAuthenticated()) {
            var authorities = auth.getAuthorities();
            allowed = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority()));
        }
        if (!allowed) { api.setCode(1006); api.setMessage("Không có quyền thực hiện thao tác này!"); return ResponseEntity.status(403).body(api); }

        User admin = userService.getById(uid);
        try {
            WithdrawRequest wr = withdrawRequestService.rejectRequest(id, admin, reason);
            WithdrawRequestResponse resp = WithdrawRequestResponse.builder()
                    .id(wr.getId())
                    .userId(wr.getUser().getId())
                    .walletId(wr.getWallet().getWalletId())
                    .amount(wr.getAmount())
                    .bankAccountNumber(wr.getBankAccountNumber())
                    .bankName(wr.getBankName())
                    .status(wr.getStatus())
                    .requestedAt(wr.getRequestedAt())
                    .build();
            api.setCode(0); api.setMessage("OK"); api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            api.setCode(1004); api.setMessage(e.getMessage()); return ResponseEntity.ok(api);
        }
    }
}
