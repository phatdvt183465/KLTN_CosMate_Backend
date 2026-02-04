package com.cosmate.controller;

import com.cosmate.dto.request.AddressRequest;
import com.cosmate.dto.response.AddressResponse;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/users/{userId}/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping("")
    public ResponseEntity<ApiResponse<AddressResponse>> create(@PathVariable("userId") Integer userId, @Validated @RequestBody AddressRequest request){
        AddressResponse r = addressService.create(userId, request);
        ApiResponse<AddressResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(r);
        return ResponseEntity.ok(api);
    }

    @GetMapping("")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> list(@PathVariable("userId") Integer userId){
        List<AddressResponse> list = addressService.listAllByUser(userId);
        ApiResponse<List<AddressResponse>> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(list);
        return ResponseEntity.ok(api);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> getById(@PathVariable("userId") Integer userId, @PathVariable("id") Integer id){
        // Authorization: owner OR STAFF/ADMIN/SUPERADMIN can view
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            ApiResponse<AddressResponse> api = new ApiResponse<>();
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }
        Integer currentUserId = null;
        try {
            Object principal = auth.getPrincipal();
            if (principal instanceof String) {
                String s = (String) principal;
                if (!s.equalsIgnoreCase("anonymousUser")) currentUserId = Integer.valueOf(s);
            } else if (principal instanceof Integer) currentUserId = (Integer) principal;
            else if (principal instanceof Long) currentUserId = ((Long) principal).intValue();
            else currentUserId = Integer.valueOf(principal.toString());
        } catch (Exception e) {
            // ignore parsing error, treat as unauthenticated
        }

        boolean allowed = false;
        if (currentUserId != null && currentUserId.equals(userId)) allowed = true;
        else {
            var authorities = auth.getAuthorities();
            allowed = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority()));
        }

        if (!allowed) {
            ApiResponse<AddressResponse> api = new ApiResponse<>();
            api.setCode(1006);
            api.setMessage("Không có quyền thực hiện thao tác này!");
            return ResponseEntity.status(403).body(api);
        }

        AddressResponse r = addressService.getById(userId, id);
        ApiResponse<AddressResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(r);
        return ResponseEntity.ok(api);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> update(@PathVariable("userId") Integer userId, @PathVariable("id") Integer id, @Validated @RequestBody AddressRequest request){
        AddressResponse r = addressService.update(userId, id, request);
        ApiResponse<AddressResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(r);
        return ResponseEntity.ok(api);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("userId") Integer userId, @PathVariable("id") Integer id){
        addressService.delete(userId, id);
        ApiResponse<Void> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        return ResponseEntity.ok(api);
    }
}
