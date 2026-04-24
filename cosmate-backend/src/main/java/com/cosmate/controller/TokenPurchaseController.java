package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.TokenPurchaseResponse;
import com.cosmate.service.TokenPurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/ai-token-purchases")
@RequiredArgsConstructor
public class TokenPurchaseController {

    private final TokenPurchaseService service;

    // Initiate purchase: returns paymentUrl for VNPay/Momo, null for wallet immediate success
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<String>> initiate(@RequestParam Integer planId,
                                                        @RequestParam String paymentMethod,
                                                        @RequestParam(required = false) String returnUrl,
                                                        Principal principal) {
        ApiResponse<String> api = new ApiResponse<>();
        try {
            Integer userId = null;
            if (principal != null) {
                try { userId = Integer.valueOf(principal.getName()); } catch (Exception ignored) {}
            }
            if (userId == null) { api.setCode(1015); api.setMessage("Chưa xác thực - Vui lòng đăng nhập"); return ResponseEntity.status(401).body(api); }
            String url = service.initiatePurchase(userId, planId, paymentMethod, returnUrl);
            api.setCode(0); api.setMessage("OK"); api.setResult(url);
            return ResponseEntity.ok(api);
        } catch (Exception ex) {
            api.setCode(9999); api.setMessage(ex.getMessage()); return ResponseEntity.internalServerError().body(api);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TokenPurchaseResponse>> getById(@PathVariable Integer id, Principal principal) {
        ApiResponse<TokenPurchaseResponse> api = new ApiResponse<>();
        try {
            Integer userId = null;
            if (principal != null) {
                try { userId = Integer.valueOf(principal.getName()); } catch (Exception ignored) {}
            }
            TokenPurchaseResponse resp = service.getById(id, userId);
            api.setCode(0); api.setMessage("OK"); api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (com.cosmate.exception.AppException ae) {
            api.setCode(ae.getErrorCode().getCode()); api.setMessage(ae.getErrorCode().getMessage()); return ResponseEntity.status(403).body(api);
        } catch (Exception ex) {
            api.setCode(9999); api.setMessage(ex.getMessage()); return ResponseEntity.internalServerError().body(api);
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TokenPurchaseResponse>>> getAll() {
        ApiResponse<List<TokenPurchaseResponse>> api = new ApiResponse<>();
        try {
            // require staff+ role
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean isStaff = false;
            if (auth != null && auth.isAuthenticated()) {
                for (org.springframework.security.core.GrantedAuthority ga : auth.getAuthorities()) {
                    if ("ROLE_STAFF".equals(ga.getAuthority()) || "ROLE_ADMIN".equals(ga.getAuthority()) || "ROLE_SUPERADMIN".equals(ga.getAuthority())) { isStaff = true; break; }
                }
            }
            if (!isStaff) { api.setCode(1006); api.setMessage("Không có quyền thực hiện thao tác này!"); return ResponseEntity.status(403).body(api); }

            List<TokenPurchaseResponse> list = service.getAll();
            api.setCode(0); api.setMessage("OK"); api.setResult(list);
            return ResponseEntity.ok(api);
        } catch (Exception ex) {
            api.setCode(9999); api.setMessage(ex.getMessage()); return ResponseEntity.internalServerError().body(api);
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<TokenPurchaseResponse>>> getByUser(@PathVariable Integer userId, Principal principal) {
        ApiResponse<List<TokenPurchaseResponse>> api = new ApiResponse<>();
        try {
            Integer requesterId = null;
            if (principal != null) {
                try { requesterId = Integer.valueOf(principal.getName()); } catch (Exception ignored) {}
            }

            // allow if requester is the same user
            boolean allowed = false;
            if (requesterId != null && requesterId.equals(userId)) allowed = true;

            // or allow if staff+
            if (!allowed) {
                org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    for (org.springframework.security.core.GrantedAuthority ga : auth.getAuthorities()) {
                        if ("ROLE_STAFF".equals(ga.getAuthority()) || "ROLE_ADMIN".equals(ga.getAuthority()) || "ROLE_SUPERADMIN".equals(ga.getAuthority())) { allowed = true; break; }
                    }
                }
            }

            if (!allowed) { api.setCode(1006); api.setMessage("Không có quyền thực hiện thao tác này!"); return ResponseEntity.status(403).body(api); }

            List<TokenPurchaseResponse> list = service.getByUser(userId);
            api.setCode(0); api.setMessage("OK"); api.setResult(list);
            return ResponseEntity.ok(api);
        } catch (Exception ex) {
            api.setCode(9999); api.setMessage(ex.getMessage()); return ResponseEntity.internalServerError().body(api);
        }
    }
}

