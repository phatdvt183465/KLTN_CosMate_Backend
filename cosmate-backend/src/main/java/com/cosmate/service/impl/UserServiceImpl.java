package com.cosmate.service.impl;

import com.cosmate.dto.request.ChangePasswordRequest;
import com.cosmate.dto.request.RegisterRequest;
import com.cosmate.dto.request.UpdateProfileRequest;
import com.cosmate.dto.request.GoogleTokenRequest;
import com.cosmate.dto.response.UserListItem;
import com.cosmate.entity.Role;
import com.cosmate.entity.User;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.UserRepository;
import com.cosmate.security.JwtUtils;
import com.cosmate.service.UserService;
import com.cosmate.service.FirebaseStorageService;
import com.cosmate.service.WalletService;
import com.cosmate.service.ProviderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final FirebaseStorageService firebaseStorageService;
    private final WalletService walletService;
    private final ProviderService providerService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${google.client-id:}")
    private String googleClientId;

    @Override
    @Transactional
    public User register(RegisterRequest request, boolean fromGoogle, String avatarUrl) {
        // check duplicates
        if (request.getEmail() != null && userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (request.getUsername() != null && userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        // Determine desired role (default to COSPLAYER)
        Role desiredRole = Role.COSPLAYER;
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                desiredRole = Role.valueOf(request.getRole().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INVALID_KEY);
            }
        }

        // Log current Authentication for debugging authorization issues
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        if (currentAuth == null) {
            logger.debug("register: no Authentication in SecurityContext");
        } else {
            logger.debug("register: authentication present principal={} authenticated={} authorities={}",
                    currentAuth.getPrincipal(), currentAuth.isAuthenticated(), currentAuth.getAuthorities());
        }

        logger.debug("register: desiredRole={}", desiredRole);

        // Authorization rules:
        // - PROVIDER: open registration (no authenticated admin needed)
        // - COSPLAYER: open registration
        // - ADMIN: only an authenticated user with ROLE_SUPERADMIN can create ADMIN accounts
        // - STAFF: only an authenticated user with ROLE_ADMIN or ROLE_SUPERADMIN can create STAFF accounts
        if (desiredRole == Role.ADMIN || desiredRole == Role.STAFF) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean canCreate = false;
            if (auth != null && auth.isAuthenticated()) {
                for (GrantedAuthority ga : auth.getAuthorities()) {
                    if (desiredRole == Role.ADMIN) {
                        if ("ROLE_SUPERADMIN".equals(ga.getAuthority())) {
                            canCreate = true; break;
                        }
                    } else { // STAFF
                        if ("ROLE_ADMIN".equals(ga.getAuthority()) || "ROLE_SUPERADMIN".equals(ga.getAuthority())) {
                            canCreate = true; break;
                        }
                    }
                }
            }
            logger.debug("register: canCreate={}", canCreate);
            if (!canCreate) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
        }

        User.UserBuilder builder = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .phone(request.getPhone())
                .roles(new HashSet<>(Collections.singleton(desiredRole)));

        if (request.getFullName() != null) builder.fullName(request.getFullName());
        if (avatarUrl != null) builder.avatarUrl(avatarUrl);

        User user = builder.build();

        // If registering via Google, skip password; for other flows, password required except when creating Admin/Staff by admin? Keep password required for all except fromGoogle
        if (!fromGoogle) {
            if (request.getPassword() == null) throw new AppException(ErrorCode.INVALID_KEY);
            String hash = passwordEncoder.encode(request.getPassword());
            user.setPasswordHash(hash);
        }

        User saved = userRepository.save(user);
        // Automatically create wallet for COSPLAYER or PROVIDER
        try {
            if (saved.getRoles() != null) {
                boolean createWallet = saved.getRoles().stream().anyMatch(r -> r == Role.COSPLAYER || r == Role.PROVIDER);
                if (createWallet) {
                    walletService.createForUser(saved);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to create wallet for user {}: {}", saved.getId(), e.getMessage(), e);
        }

        // If the user was created with PROVIDER role, create a Provider entity (fields null by default). The Providers table references Users by user_id.
        try {
            if (saved.getRoles() != null && saved.getRoles().stream().anyMatch(r -> r == Role.PROVIDER)) {
                providerService.createForUser(saved.getId());
            }
        } catch (Exception e) {
            logger.error("Failed to create provider record for user {}: {}", saved.getId(), e.getMessage(), e);
        }
        return saved;
    }

    @Override
    public String authenticate(String usernameOrEmail, String password) {
        User user = userRepository.findByEmail(usernameOrEmail)
                .or(() -> userRepository.findByUsername(usernameOrEmail))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        // prevent banned users from logging in
        if (user.getStatus() != null && "BANNED".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_BANNED);
        }

        if (user.getPasswordHash() == null) throw new AppException(ErrorCode.INVALID_CREDENTIALS);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        List<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toList());
        // convert Integer id to Long to match JwtUtils.generateToken signature
        Long userIdLong = user.getId() == null ? null : user.getId().longValue();
        return jwtUtils.generateToken(userIdLong, roles);
    }

    @Override
    @Transactional
    public User updateProfile(Integer userId, UpdateProfileRequest request, MultipartFile avatar) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));

        // username is not allowed to be changed via profile update

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());

        // upload avatar if provided; service handles upload and sets avatarUrl
        if (avatar != null && !avatar.isEmpty()) {
            try {
                String filename = avatar.getOriginalFilename() == null ? "avatar" : avatar.getOriginalFilename();
                String destination = "users/" + userId + "/avatar_" + System.currentTimeMillis() + "_" + filename;
                String url = firebaseStorageService.uploadFile(avatar, destination);
                user.setAvatarUrl(url);
                // If user is a PROVIDER, mirror avatar to Provider record
                try {
                    if (user.getRoles() != null && user.getRoles().stream().anyMatch(r -> r == Role.PROVIDER)) {
                        providerService.updateAvatarForUser(userId, url);
                    }
                } catch (Exception e) {
                    logger.error("Failed to mirror avatar to provider for user {}: {}", userId, e.getMessage(), e);
                }
            } catch (Exception e) {
                // Log the upload failure but do not abort the whole profile update
                logger.error("Failed to upload avatar for user {}: {}. Continuing without updating avatar.", userId, e.getMessage(), e);
                // keep existing avatarUrl in user (no change)
            }
        }

        return userRepository.save(user);
    }

    // New: update only avatar
    @Override
    @Transactional
    public User updateAvatar(Integer userId, MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) throw new AppException(ErrorCode.INVALID_KEY);
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        try {
            String filename = avatar.getOriginalFilename() == null ? "avatar" : avatar.getOriginalFilename();
            String destination = "users/" + userId + "/avatar_" + System.currentTimeMillis() + "_" + filename;
            String url = firebaseStorageService.uploadFile(avatar, destination);
            user.setAvatarUrl(url);
            // If user is a PROVIDER, mirror avatar to Provider record
            try {
                if (user.getRoles() != null && user.getRoles().stream().anyMatch(r -> r == Role.PROVIDER)) {
                    providerService.updateAvatarForUser(userId, url);
                }
            } catch (Exception e) {
                logger.error("Failed to mirror avatar to provider for user {}: {}", userId, e.getMessage(), e);
            }
        } catch (Exception e) {
            logger.error("Failed to upload avatar for user {}: {}", userId, e.getMessage(), e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(Integer userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private void requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = false;
        if (auth != null && auth.isAuthenticated()) {
            for (GrantedAuthority ga : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(ga.getAuthority()) || "ROLE_SUPERADMIN".equals(ga.getAuthority())) {
                    isAdmin = true; break;
                }
            }
        }
        if (!isAdmin) throw new AppException(ErrorCode.FORBIDDEN);
    }

    @Override
    @Transactional
    public void lockUser(Integer targetUserId) {
        requireAdmin();
        User user = userRepository.findById(targetUserId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        user.setStatus("INACTIVE");
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void unlockUser(Integer targetUserId) {
        requireAdmin();
        User user = userRepository.findById(targetUserId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        user.setStatus("ACTIVE");
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void banUser(Integer targetUserId) {
        requireAdmin();
        User user = userRepository.findById(targetUserId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        user.setStatus("BANNED");
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void unbanUser(Integer targetUserId) {
        requireAdmin();
        User user = userRepository.findById(targetUserId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        user.setStatus("ACTIVE");
        userRepository.save(user);
    }

    @Override
    public List<UserListItem> listUsers() {
        return userRepository.findAll().stream().map(u -> UserListItem.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .phone(u.getPhone())
                .status(u.getStatus())
                .roles(u.getRoles().stream().map(Enum::name).collect(Collectors.toSet()))
                .createdAt(u.getCreatedAt())
                .build()).collect(Collectors.toList());
    }

    @Override
    public String loginWithGoogleToken(GoogleTokenRequest request) {
        Map<String, Object> payload = verifyGoogleIdToken(request.getIdToken());
        if (payload == null) throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        String email = (String) payload.get("email");
        if (email == null) throw new AppException(ErrorCode.INVALID_CREDENTIALS);

        User user = userRepository.findByEmail(email).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        // check banned
        if (user.getStatus() != null && "BANNED".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_BANNED);
        }
        List<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toList());
        Long userIdLong = user.getId() == null ? null : user.getId().longValue();
        return jwtUtils.generateToken(userIdLong, roles);
    }

    @Override
    @Transactional
    public String registerWithGoogleToken(GoogleTokenRequest request) {
        Map<String, Object> payload = verifyGoogleIdToken(request.getIdToken());
        if (payload == null) throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        String email = (String) payload.get("email");
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");
        if (email == null) throw new AppException(ErrorCode.INVALID_EMAIL);

        Optional<User> existing = userRepository.findByEmail(email);
        User user;
        if (existing.isPresent()) {
            user = existing.get();
            // update profile fields
            if (name != null) user.setFullName(name);
            if (picture != null) user.setAvatarUrl(picture);
            user = userRepository.save(user);
        } else {
            RegisterRequest r = new RegisterRequest();
            r.setEmail(email);
            r.setFullName(name);
            user = register(r, true, picture);
        }

        if (user.getStatus() != null && "BANNED".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_BANNED);
        }

        List<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toList());
        Long userIdLong = user.getId() == null ? null : user.getId().longValue();
        return jwtUtils.generateToken(userIdLong, roles);
    }

    @Override
    public User getById(Integer userId) {
        return userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Verify Google id_token using Google's tokeninfo endpoint.
     * Returns payload map if valid, otherwise null.
     */
    private Map<String, Object> verifyGoogleIdToken(String idToken) {
        try {
            // Google's tokeninfo endpoint: https://oauth2.googleapis.com/tokeninfo?id_token=XYZ
            String urlStr = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            if (code != 200) {
                logger.debug("Google tokeninfo returned status {}", code);
                return null;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                Map<String, Object> map = objectMapper.readValue(sb.toString(), Map.class);
                // Validate aud (client_id) if configured
                if (googleClientId != null && !googleClientId.isBlank()) {
                    Object audObj = map.get("aud");
                    String aud = audObj == null ? null : audObj.toString();
                    if (!googleClientId.equals(aud)) {
                        logger.debug("Google id_token aud mismatch: expected='{}' actual='{}'", googleClientId, aud);
                        return null;
                    }
                } else {
                    logger.debug("google.client-id not set; skipping aud validation");
                }
                // Optionally validate issuer
                Object issObj = map.get("iss");
                if (issObj != null) {
                    String iss = issObj.toString();
                    if (!"accounts.google.com".equals(iss) && !"https://accounts.google.com".equals(iss)) {
                        logger.debug("Unexpected token issuer='{}'", iss);
                        return null;
                    }
                }
                return map;
            }
        } catch (Exception e) {
            logger.debug("verifyGoogleIdToken error: {}", e.getMessage());
            return null;
        }
    }
}
