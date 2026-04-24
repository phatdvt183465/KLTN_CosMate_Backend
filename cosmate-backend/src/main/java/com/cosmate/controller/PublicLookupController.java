package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.entity.Provider;
import com.cosmate.entity.User;
import com.cosmate.repository.UserRepository;
import com.cosmate.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicLookupController {

    private final UserRepository userRepository;
    private final ProviderService providerService;
    private static final Logger log = LoggerFactory.getLogger(PublicLookupController.class);

    @GetMapping("/user-name/{userId}")
    public ResponseEntity<ApiResponse<String>> getUserNameById(@PathVariable("userId") Integer userId) {
        ApiResponse<String> api = new ApiResponse<>();
        try {
            User u = userRepository.findById(userId).orElse(null);
            if (u == null) {
                api.setCode(1004);
                api.setMessage("User not found");
                return ResponseEntity.status(404).body(api);
            }
            String name = (u.getFullName() == null || u.getFullName().isBlank()) ? u.getUsername() : u.getFullName();
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(name);
            return ResponseEntity.ok(api);
        } catch (Exception ex) {
            log.error("Error fetching user name for id {}: {}", userId, ex.getMessage(), ex);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + ex.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @GetMapping("/shop-name/{providerId}")
    public ResponseEntity<ApiResponse<String>> getShopNameByProviderId(@PathVariable("providerId") Integer providerId) {
        ApiResponse<String> api = new ApiResponse<>();
        try {
            Provider p = providerService.getById(providerId);
            if (p == null) {
                api.setCode(1005);
                api.setMessage("Provider not found");
                return ResponseEntity.status(404).body(api);
            }
            String shopName = p.getShopName() == null ? "" : p.getShopName();
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(shopName);
            return ResponseEntity.ok(api);
        } catch (Exception ex) {
            log.error("Error fetching shop name for provider id {}: {}", providerId, ex.getMessage(), ex);
            api.setCode(99999);
            api.setMessage("Unexpected server error: " + ex.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }
}

