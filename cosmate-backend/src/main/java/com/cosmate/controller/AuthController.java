package com.cosmate.controller;

import com.cosmate.dto.request.GoogleRegisterRequest;
import com.cosmate.dto.request.QrApproveRequest;
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
import java.util.UUID;
import java.time.LocalDateTime;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.cosmate.entity.Token;
import com.cosmate.repository.TokenRepository;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final ProviderService providerService;
    private final ActivationService activationService;
    private final PasswordResetService passwordResetService;
    private final TokenRepository tokenRepository;
    private final SimpMessagingTemplate messagingTemplate;

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

    // QR login: generate a sessionId (token) for the web client to display as QR
    @GetMapping("/qr-generate")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateQrSession() {
        String sessionId = UUID.randomUUID().toString();
        Token token = Token.builder()
                .token(sessionId)
                .type("QR_LOGIN_SESSION")
                .user(null)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();

        tokenRepository.save(token);

        ApiResponse<Map<String, String>> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(Map.of("sessionId", sessionId));
        return ResponseEntity.ok(api);
    }

    // DEV-only debug endpoint to inspect a QR token record
    @GetMapping("/qr-debug")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debugToken(@RequestParam("sessionId") String sessionId) {
        ApiResponse<Map<String, Object>> api = new ApiResponse<>();
        var tokenOpt = tokenRepository.findByToken(sessionId);
        if (tokenOpt.isEmpty()) {
            api.setCode(1);
            api.setMessage("Not found");
            api.setResult(null);
            return ResponseEntity.badRequest().body(api);
        }
        Token tok = tokenOpt.get();
        // Safely extract user id; token.user is LAZY and may throw LazyInitializationException
        Integer uid = null;
        try {
            if (tok.getUser() != null) uid = tok.getUser().getId();
        } catch (Exception ignored) {
            uid = null;
        }
        Map<String, Object> m = Map.of(
                "token", tok.getToken(),
                "type", tok.getType(),
                "used", tok.getUsed(),
                "expiresAt", tok.getExpiresAt(),
                "userId", uid
        );
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(m);
        return ResponseEntity.ok(api);
    }

    // Mobile approves: authenticated mobile user posts sessionId to claim it
    @PostMapping("/qr-approve")
    public ResponseEntity<ApiResponse<Map<String, String>>> approveQr(@Valid @RequestBody QrApproveRequest request) {
        ApiResponse<Map<String, String>> api = new ApiResponse<>();

        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            api.setCode(1);
            api.setMessage("Unauthenticated");
            return ResponseEntity.status(401).body(api);
        }

        // Load user and token record, perform check/update in Java to avoid DB-specific functions
        var user = userService.getById(currentUserId);
        var tokenOpt = tokenRepository.findByToken(request.getSessionId());
        if (tokenOpt.isEmpty()) {
            api.setCode(1);
            api.setMessage("Invalid or expired session");
            return ResponseEntity.badRequest().body(api);
        }

        Token tok = tokenOpt.get();
        // used may be null in DB; treat null as used=false
        if (Boolean.TRUE.equals(tok.getUsed())) {
            api.setCode(1);
            api.setMessage("Invalid or expired session");
            return ResponseEntity.badRequest().body(api);
        }

        if (tok.getExpiresAt() == null || !tok.getExpiresAt().isAfter(LocalDateTime.now())) {
            api.setCode(1);
            api.setMessage("Invalid or expired session");
            return ResponseEntity.badRequest().body(api);
        }

        // claim the token for this user
        tok.setUser(user);
        tok.setUsed(true);
        tokenRepository.save(tok);

        // Build JWT for the user and notify web client via WebSocket
        List<String> roles = user.getRole() == null ? List.of() : List.of(user.getRole().getRoleName());
        Long userIdLong = user.getId() == null ? null : user.getId().longValue();
        Long providerIdLong = null;
        try {
            if (user.getRole() != null) {
                // reuse provider check logic if available
                try {
                    var prov = providerService.getByUserId(user.getId());
                    if (prov != null && prov.getId() != null) providerIdLong = prov.getId().longValue();
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }

        String jwt = jwtUtils.generateToken(userIdLong, roles, providerIdLong);

        // Send event to the room named by sessionId
        var payload = Map.of("event", "qr_approved", "accessToken", jwt);
        messagingTemplate.convertAndSend("/topic/qr/" + request.getSessionId(), payload);

        api.setCode(0);
        api.setMessage("OK");
        api.setResult(Map.of("sent", "true"));
        return ResponseEntity.ok(api);
    }

    private Integer getCurrentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
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
}
