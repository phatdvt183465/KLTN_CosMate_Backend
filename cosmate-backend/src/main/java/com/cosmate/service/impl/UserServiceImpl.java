package com.cosmate.service.impl;

import com.cosmate.dto.request.ChangePasswordRequest;
import com.cosmate.dto.request.RegisterRequest;
import com.cosmate.dto.request.UpdateProfileRequest;
import com.cosmate.dto.request.GoogleTokenRequest;
import com.cosmate.dto.response.UserListItem;
import com.cosmate.entity.Role;
import com.cosmate.entity.RoleEntity;
import com.cosmate.entity.User;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.UserRepository;
import com.cosmate.repository.RoleRepository;
import com.cosmate.security.JwtUtils;
import com.cosmate.service.UserService;
import com.cosmate.service.FirebaseStorageService;
import com.cosmate.service.WalletService;
import com.cosmate.service.ProviderService;
import com.cosmate.util.RoleUtils;
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

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private final RoleRepository roleRepository;
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
        String desiredRoleName = Role.COSPLAYER.name();
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                desiredRoleName = request.getRole().trim().toUpperCase(Locale.ROOT);
                Role.valueOf(desiredRoleName); // validate
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INVALID_KEY);
            }
        }

        // Authorization checks for ADMIN/STAFF creation
        if ("ADMIN".equals(desiredRoleName) || "STAFF".equals(desiredRoleName)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean canCreate = false;
            if (auth != null && auth.isAuthenticated()) {
                for (GrantedAuthority ga : auth.getAuthorities()) {
                    if ("ADMIN".equals(desiredRoleName)) {
                        if ("ROLE_SUPERADMIN".equals(ga.getAuthority())) { canCreate = true; break; }
                    } else { // STAFF
                        if ("ROLE_ADMIN".equals(ga.getAuthority()) || "ROLE_SUPERADMIN".equals(ga.getAuthority())) { canCreate = true; break; }
                    }
                }
            }
            if (!canCreate) throw new AppException(ErrorCode.FORBIDDEN);
        }

        RoleEntity roleEntity = roleRepository.findByRoleName(desiredRoleName).orElseThrow(() -> new AppException(ErrorCode.INVALID_KEY));

        User.UserBuilder builder = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(roleEntity);

        if (request.getFullName() != null) builder.fullName(request.getFullName());
        if (avatarUrl != null) builder.avatarUrl(avatarUrl);

        User user = builder.build();

        if (!fromGoogle) {
            if (request.getPassword() == null) throw new AppException(ErrorCode.INVALID_KEY);
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        User saved = userRepository.save(user);

        // create wallet for COSPLAYER or provider roles
        try {
            if (saved.getRole() != null) {
                Role savedEnum = null;
                try { savedEnum = Role.valueOf(saved.getRole().getRoleName()); } catch (IllegalArgumentException ignored) {}
                if (savedEnum != null && (savedEnum == Role.COSPLAYER || RoleUtils.isProviderRole(savedEnum))) {
                    walletService.createForUser(saved);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to create wallet for user {}: {}", saved.getId(), e.getMessage(), e);
        }

        // create provider record for provider roles
        try {
            if (saved.getRole() != null) {
                Role savedEnum = null;
                try { savedEnum = Role.valueOf(saved.getRole().getRoleName()); } catch (IllegalArgumentException ignored) {}
                if (savedEnum != null && RoleUtils.isProviderRole(savedEnum)) {
                    providerService.createForUser(saved.getId());
                }
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

        if (user.getStatus() != null && "BANNED".equalsIgnoreCase(user.getStatus())) throw new AppException(ErrorCode.ACCOUNT_BANNED);
        if (user.getPasswordHash() == null) throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) throw new AppException(ErrorCode.INVALID_CREDENTIALS);

        List<String> roles = user.getRole() == null ? Collections.emptyList() : List.of(user.getRole().getRoleName());
        Long userIdLong = user.getId() == null ? null : user.getId().longValue();

        Long providerIdLong = null;
        if (user.getRole() != null) {
            Role savedEnum = null;
            try { savedEnum = Role.valueOf(user.getRole().getRoleName()); } catch (IllegalArgumentException ignored) {}
            if (savedEnum != null && RoleUtils.isProviderRole(savedEnum)) {
                try {
                    var prov = providerService.getByUserId(user.getId());
                    if (prov != null && prov.getId() != null) providerIdLong = prov.getId().longValue();
                } catch (AppException ignored) { }
            }
        }

        return jwtUtils.generateToken(userIdLong, roles, providerIdLong);
    }

    @Override
    @Transactional
    public User updateProfile(Integer userId, UpdateProfileRequest request, MultipartFile avatar) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());

        if (avatar != null && !avatar.isEmpty()) {
            try {
                String filename = avatar.getOriginalFilename() == null ? "avatar" : avatar.getOriginalFilename();
                String destination = "users/" + userId + "/avatar_" + System.currentTimeMillis() + "_" + filename;
                String url = firebaseStorageService.uploadFile(avatar, destination);
                user.setAvatarUrl(url);
                try {
                    if (user.getRole() != null) {
                        Role savedEnum = null;
                        try { savedEnum = Role.valueOf(user.getRole().getRoleName()); } catch (IllegalArgumentException ignored) {}
                        if (savedEnum != null && RoleUtils.isProviderRole(savedEnum)) {
                            providerService.updateAvatarForUser(userId, url);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to mirror avatar to provider for user {}: {}", userId, e.getMessage(), e);
                }
            } catch (Exception e) {
                logger.error("Failed to upload avatar for user {}: {}. Continuing without updating avatar.", userId, e.getMessage(), e);
            }
        }

        return userRepository.save(user);
    }

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
            try {
                if (user.getRole() != null) {
                    Role savedEnum = null;
                    try { savedEnum = Role.valueOf(user.getRole().getRoleName()); } catch (IllegalArgumentException ignored) {}
                    if (savedEnum != null && RoleUtils.isProviderRole(savedEnum)) {
                        providerService.updateAvatarForUser(userId, url);
                    }
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
                if ("ROLE_ADMIN".equals(ga.getAuthority()) || "ROLE_SUPERADMIN".equals(ga.getAuthority())) { isAdmin = true; break; }
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
                .role(u.getRole() == null ? null : u.getRole().getRoleName())
                .build()).toList();
    }

    @Override
    public String loginWithGoogleToken(GoogleTokenRequest request) {
        Map<String, Object> payload = verifyGoogleIdToken(request.getIdToken());
        if (payload == null) throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        String email = (String) payload.get("email");
        if (email == null) throw new AppException(ErrorCode.INVALID_CREDENTIALS);

        User user = userRepository.findByEmail(email).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != null && "BANNED".equalsIgnoreCase(user.getStatus())) throw new AppException(ErrorCode.ACCOUNT_BANNED);

        List<String> roles = user.getRole() == null ? Collections.emptyList() : List.of(user.getRole().getRoleName());
        Long userIdLong = user.getId() == null ? null : user.getId().longValue();

        Long providerIdLong = null;
        if (user.getRole() != null) {
            Role savedEnum = null;
            try { savedEnum = Role.valueOf(user.getRole().getRoleName()); } catch (IllegalArgumentException ignored) {}
            if (savedEnum != null && RoleUtils.isProviderRole(savedEnum)) {
                try {
                    var prov = providerService.getByUserId(user.getId());
                    if (prov != null && prov.getId() != null) providerIdLong = prov.getId().longValue();
                } catch (AppException ignored) { }
            }
        }

        return jwtUtils.generateToken(userIdLong, roles, providerIdLong);
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
            if (name != null) user.setFullName(name);
            if (picture != null) user.setAvatarUrl(picture);
            user = userRepository.save(user);
        } else {
            RegisterRequest r = new RegisterRequest();
            r.setEmail(email);
            r.setFullName(name);
            user = register(r, true, picture);
        }

        if (user.getStatus() != null && "BANNED".equalsIgnoreCase(user.getStatus())) throw new AppException(ErrorCode.ACCOUNT_BANNED);

        List<String> roles = user.getRole() == null ? Collections.emptyList() : List.of(user.getRole().getRoleName());
        Long userIdLong = user.getId() == null ? null : user.getId().longValue();

        Long providerIdLong = null;
        if (user.getRole() != null) {
            Role savedEnum = null;
            try { savedEnum = Role.valueOf(user.getRole().getRoleName()); } catch (IllegalArgumentException ignored) {}
            if (savedEnum != null && RoleUtils.isProviderRole(savedEnum)) {
                try {
                    var prov = providerService.getByUserId(user.getId());
                    if (prov != null && prov.getId() != null) providerIdLong = prov.getId().longValue();
                } catch (AppException ignored) { }
            }
        }

        return jwtUtils.generateToken(userIdLong, roles, providerIdLong);
    }

    @Override
    public User getById(Integer userId) {
        return userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Verify Google id_token using Google's tokeninfo endpoint.
     */
    private Map<String, Object> verifyGoogleIdToken(String idToken) {
        try {
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
                if (googleClientId != null && !googleClientId.isBlank()) {
                    Object audObj = map.get("aud");
                    String aud = audObj == null ? null : audObj.toString();
                    if (!googleClientId.equals(aud)) return null;
                }
                Object issObj = map.get("iss");
                if (issObj != null) {
                    String iss = issObj.toString();
                    if (!"accounts.google.com".equals(iss) && !"https://accounts.google.com".equals(iss)) return null;
                }
                return map;
            }
        } catch (Exception e) {
            logger.debug("verifyGoogleIdToken error: {}", e.getMessage());
            return null;
        }
    }
}
