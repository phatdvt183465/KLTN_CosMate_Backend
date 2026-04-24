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
import com.cosmate.service.ActivationService;
import com.cosmate.service.ProviderService;
import com.cosmate.service.PasswordResetService;
import com.cosmate.dto.request.ResetPasswordRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final ProviderService providerService;
    private final ActivationService activationService;
    private final PasswordResetService passwordResetService;

    // Only accept JSON register requests (no avatar upload through this API)
    @PostMapping(value = "/register", consumes = { MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<ApiResponse<UserResponse>> registerJson(@Valid @RequestBody RegisterRequest request){
        // avatar not provided -> null
        User user = userService.register(request, false, null);
        UserResponse resp = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
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

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request){
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
    public ResponseEntity<ApiResponse<AuthResponse>> registerGoogle(@Valid @RequestBody GoogleRegisterRequest request){
        RegisterRequest r = new RegisterRequest();
        r.setEmail(request.getEmail());
        r.setFullName(request.getFullName());
        // username left null
        User user = userService.register(r, true, request.getAvatarUrl());
        List<String> roles = user.getRole() == null ? List.of() : List.of(user.getRole().getRoleName());
        Long userIdLong = user.getId() == null ? null : user.getId().longValue();

        Long providerIdLong = null;
        try {
            var prov = providerService.getByUserId(user.getId());
            if (prov != null && prov.getId() != null) providerIdLong = prov.getId().longValue();
        } catch (Exception ignored) {
            // no provider record
        }

        String token = jwtUtils.generateToken(userIdLong, roles, providerIdLong);

        AuthResponse auth = new AuthResponse();
        auth.setToken(token);

        ApiResponse<AuthResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(auth);
        return ResponseEntity.ok(api);
    }

    @PostMapping("/google/login")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleTokenRequest request) {
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
    public ResponseEntity<ApiResponse<AuthResponse>> googleRegister(@Valid @RequestBody GoogleTokenRequest request) {
        String token = userService.registerWithGoogleToken(request);
        AuthResponse auth = new AuthResponse();
        auth.setToken(token);
        ApiResponse<AuthResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(auth);
        return ResponseEntity.ok(api);
    }

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    // Activation endpoint used by frontend link
    @GetMapping("/activate")
    public ResponseEntity<Void> activate(@RequestParam("token") String token) {
        try {
            activationService.activate(token);
            String redirect = frontendUrl + "/login";
            return ResponseEntity.status(302).header("Location", redirect).build();
        } catch (Exception ex) {
            String redirect = frontendUrl + "/login";
            return ResponseEntity.status(302).header("Location", redirect).build();
        }
    }

    // Request a password reset link to be sent to email (identifier = email or username)
    @PostMapping("/password-reset-request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody com.cosmate.dto.request.PasswordResetRequest request) {
        String identifier = request.getIdentifier();
        ApiResponse<Void> api = new ApiResponse<>();
        try {
            passwordResetService.createTokenForIdentifier(identifier);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(null);
            return ResponseEntity.ok(api);
        } catch (Exception ex) {
            api.setCode(1);
            api.setMessage(ex.getMessage());
            api.setResult(null);
            return ResponseEntity.badRequest().body(api);
        }
    }

    // Link the user clicks in email -> backend validates and then redirects to frontend reset page with token
    @GetMapping("/password-reset")
    public ResponseEntity<Void> passwordResetRedirect(@RequestParam("token") String token) {
        String redirect = frontendUrl + "/reset-password?token=" + token;
        return ResponseEntity.status(302).header("Location", redirect).build();
    }

    // Frontend will post new password + token to backend to complete reset
    @PostMapping("/password-reset")
    public ResponseEntity<ApiResponse<Void>> performPasswordReset(@Valid @RequestBody ResetPasswordRequest request) {
        ApiResponse<Void> api = new ApiResponse<>();
        try {
            passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(null);
            return ResponseEntity.ok(api);
        } catch (Exception ex) {
            api.setCode(1);
            api.setMessage(ex.getMessage());
            api.setResult(null);
            return ResponseEntity.badRequest().body(api);
        }
    }
}
