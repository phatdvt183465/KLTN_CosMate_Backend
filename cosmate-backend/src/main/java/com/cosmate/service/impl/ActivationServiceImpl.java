package com.cosmate.service.impl;

import com.cosmate.entity.Token;
import com.cosmate.entity.User;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.TokenRepository;
import com.cosmate.repository.UserRepository;
import com.cosmate.service.ActivationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivationServiceImpl implements ActivationService {

    private static final Logger logger = LoggerFactory.getLogger(ActivationServiceImpl.class);

    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.activation.token.expiration-hours:24}")
    private long tokenExpiryHours;

    @Value("${backend.url:http://localhost:8080}")
    private String backendUrl;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Override
    @Transactional
    public Token createTokenForUser(User user) {
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        Token at = Token.builder()
                .token(token)
                .type("ACTIVATION")
                .user(user)
                .createdAt(now)
                .expiresAt(now.plusHours(tokenExpiryHours))
                .used(false)
                .build();
        Token saved = tokenRepository.save(at);

        // send email
        try {
            String link = sanitizeBackendUrl(backendUrl) + "/api/auth/activate?token=" + token;
            SimpleMailMessage msg = new SimpleMailMessage();
            if (mailFrom != null && !mailFrom.isBlank()) msg.setFrom(mailFrom);
            msg.setTo(user.getEmail());
            msg.setSubject("Kích hoạt tài khoản CosMate");
            String body = "Xin chào " + (user.getFullName() == null ? "" : user.getFullName()) + ",\n\n" +
                    "Cám ơn bạn đã đăng ký tài khoản trên CosMate. Vui lòng nhấn vào liên kết dưới đây để kích hoạt tài khoản của bạn:\n" +
                    link + "\n\n" +
                    "Liên kết sẽ hết hạn sau " + tokenExpiryHours + " giờ. Nếu bạn không đăng ký, hãy bỏ qua email này.";
            msg.setText(body);
            mailSender.send(msg);
            logger.info("Sent activation email to {}", user.getEmail());
        } catch (Exception ex) {
            logger.error("Failed to send activation email to {}: {}", user.getEmail(), ex.getMessage(), ex);
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
    public void activate(String token) {
        Optional<Token> opt = tokenRepository.findByToken(token);
        if (opt.isEmpty()) throw new AppException(ErrorCode.INVALID_KEY);
        Token at = opt.get();
        if (!"ACTIVATION".equalsIgnoreCase(at.getType())) throw new AppException(ErrorCode.INVALID_KEY);
        if (Boolean.TRUE.equals(at.getUsed())) throw new AppException(ErrorCode.INVALID_KEY);
        if (at.getExpiresAt() != null && at.getExpiresAt().isBefore(LocalDateTime.now())) throw new AppException(ErrorCode.INVALID_KEY);

        User user = userRepository.findById(at.getUser().getId()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setStatus("ACTIVE");
        userRepository.save(user);

        at.setUsed(true);
        tokenRepository.save(at);
    }
}


