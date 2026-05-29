package com.cosmate.service.impl;

import com.cosmate.entity.Token;
import com.cosmate.entity.User;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.TokenRepository;
import com.cosmate.repository.UserRepository;
import com.cosmate.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.password-reset.token.expiration-hours:2}")
    private long tokenExpiryHours;

    @Value("${backend.url:http://localhost:8080}")
    private String backendUrl;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public Token createTokenForIdentifier(String identifier) {
        Optional<User> uOpt = userRepository.findByEmail(identifier);
        if (uOpt.isEmpty()) {
            uOpt = userRepository.findByUsername(identifier);
        }
        if (uOpt.isEmpty()) throw new AppException(ErrorCode.USER_NOT_FOUND);
        User user = uOpt.get();

        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        Token prt = Token.builder()
                .token(token)
                .type("PASSWORD_RESET")
                .user(user)
                .createdAt(now)
                .expiresAt(now.plusHours(tokenExpiryHours))
                .used(false)
                .build();
        Token saved = tokenRepository.save(prt);

        // send email with backend activation link
        try {
            String link = sanitizeBackendUrl(backendUrl) + "/api/auth/password-reset?token=" + token;
            SimpleMailMessage msg = new SimpleMailMessage();
            if (mailFrom != null && !mailFrom.isBlank()) msg.setFrom(mailFrom);
            msg.setTo(user.getEmail());
            msg.setSubject("Đặt lại mật khẩu CosMate");
            String body = "Xin chào " + (user.getFullName() == null ? "" : user.getFullName()) + ",\n\n" +
                    "Bạn đã yêu cầu đặt lại mật khẩu. Vui lòng nhấn vào liên kết dưới đây để thiết lập mật khẩu mới:\n" +
                    link + "\n\n" +
                    "Liên kết sẽ hết hạn sau " + tokenExpiryHours + " giờ. Nếu bạn không yêu cầu, hãy bỏ qua email này.";
            msg.setText(body);
            mailSender.send(msg);
            logger.info("Sent password reset email to {}", user.getEmail());
        } catch (Exception ex) {
            logger.error("Failed to send password reset email to {}: {}", user.getEmail(), ex.getMessage(), ex);
        }

        return saved;
    }

    // Normalize backend URL: remove trailing slash and remove a leading "www." from the host if present
    private String sanitizeBackendUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        if (u.isEmpty()) return "";
        // remove trailing slash
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);

        String prefix = "";
        if (u.startsWith("http://")) {
            prefix = "http://";
            u = u.substring(7);
        } else if (u.startsWith("https://")) {
            prefix = "https://";
            u = u.substring(8);
        }

        if (u.startsWith("www.")) {
            u = u.substring(4);
        }

        return prefix + u;
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Optional<Token> opt = tokenRepository.findByToken(token);
        if (opt.isEmpty()) throw new AppException(ErrorCode.INVALID_KEY);
        Token prt = opt.get();
        if (!"PASSWORD_RESET".equalsIgnoreCase(prt.getType())) throw new AppException(ErrorCode.INVALID_KEY);
        if (Boolean.TRUE.equals(prt.getUsed())) throw new AppException(ErrorCode.INVALID_KEY);
        if (prt.getExpiresAt() != null && prt.getExpiresAt().isBefore(LocalDateTime.now())) throw new AppException(ErrorCode.INVALID_KEY);

        User user = userRepository.findById(prt.getUser().getId()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        prt.setUsed(true);
        tokenRepository.save(prt);
    }
}


