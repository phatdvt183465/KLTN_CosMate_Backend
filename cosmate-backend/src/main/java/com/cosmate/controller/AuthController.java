package com.cosmate.controller;

import com.cosmate.dto.request.GoogleRegisterRequest;
import com.cosmate.dto.request.LoginRequest;
import com.cosmate.dto.request.RegisterRequest;
import com.cosmate.dto.request.GoogleTokenRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.AuthResponse;
import com.cosmate.dto.response.UserResponse;
import com.cosmate.entity.User;
import com.cosmate.security.JwtUtils;
import com.cosmate.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Validated @RequestBody RegisterRequest request){
        User user = userService.register(request, false);
        UserResponse resp = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .phone(user.getPhone())
                .status(user.getStatus())
                .build();

        ApiResponse<UserResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest request){
        String token = userService.authenticate(request.getUsernameOrEmail(), request.getPassword());
        AuthResponse auth = new AuthResponse();
        auth.setToken(token);

        ApiResponse<AuthResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(auth);
        return ResponseEntity.ok(api);
    }

    @PostMapping("/register/google")
    public ResponseEntity<ApiResponse<AuthResponse>> registerGoogle(@RequestBody GoogleRegisterRequest request){
        RegisterRequest r = new RegisterRequest();
        r.setEmail(request.getEmail());
        r.setFullName(request.getFullName());
        r.setAvatarUrl(request.getAvatarUrl());
        // username left null
        User user = userService.register(r, true);
        List<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toList());
        Long userIdLong = user.getId() == null ? null : user.getId().longValue();
        String token = jwtUtils.generateToken(userIdLong, roles);

        AuthResponse auth = new AuthResponse();
        auth.setToken(token);

        ApiResponse<AuthResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(auth);
        return ResponseEntity.ok(api);
    }

    @PostMapping("/google/login")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@RequestBody GoogleTokenRequest request) {
        String token = userService.loginWithGoogleToken(request);
        AuthResponse auth = new AuthResponse();
        auth.setToken(token);
        ApiResponse<AuthResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(auth);
        return ResponseEntity.ok(api);
    }

    @PostMapping("/google/register")
    public ResponseEntity<ApiResponse<AuthResponse>> googleRegister(@RequestBody GoogleTokenRequest request) {
        String token = userService.registerWithGoogleToken(request);
        AuthResponse auth = new AuthResponse();
        auth.setToken(token);
        ApiResponse<AuthResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(auth);
        return ResponseEntity.ok(api);
    }
}
