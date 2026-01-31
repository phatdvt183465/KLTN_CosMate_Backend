package com.cosmate.service.impl;

import com.cosmate.dto.request.ChangePasswordRequest;
import com.cosmate.dto.request.RegisterRequest;
import com.cosmate.dto.request.UpdateProfileRequest;
import com.cosmate.dto.response.UserListItem;
import com.cosmate.entity.Role;
import com.cosmate.entity.User;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.UserRepository;
import com.cosmate.security.JwtUtils;
import com.cosmate.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public User register(RegisterRequest request, boolean fromGoogle) {
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
        // - ADMIN / STAFF: only an authenticated user with ROLE_ADMIN can create these accounts
        if (desiredRole == Role.ADMIN || desiredRole == Role.STAFF) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = false;
            if (auth != null && auth.isAuthenticated()) {
                for (GrantedAuthority ga : auth.getAuthorities()) {
                    if ("ROLE_ADMIN".equals(ga.getAuthority())) {
                        isAdmin = true;
                        break;
                    }
                }
            }
            logger.debug("register: isAdmin={}", isAdmin);
            if (!isAdmin) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
        }

        User.UserBuilder builder = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .phone(request.getPhone())
                .roles(new HashSet<>(Collections.singleton(desiredRole)));

        if (request.getFullName() != null) builder.fullName(request.getFullName());
        if (request.getAvatarUrl() != null) builder.avatarUrl(request.getAvatarUrl());

        User user = builder.build();

        // If registering via Google, skip password; for other flows, password required except when creating Admin/Staff by admin? Keep password required for all except fromGoogle
        if (!fromGoogle) {
            if (request.getPassword() == null) throw new AppException(ErrorCode.INVALID_KEY);
            String hash = passwordEncoder.encode(request.getPassword());
            user.setPasswordHash(hash);
        }

        return userRepository.save(user);
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
    public User updateProfile(Integer userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));

        // username is not allowed to be changed via profile update

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

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
                if ("ROLE_ADMIN".equals(ga.getAuthority())) {
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
}
