package com.cosmate.service.impl;

import com.cosmate.dto.request.AiTokenPlanRequest;
import com.cosmate.dto.response.AiTokenPlanResponse;
import com.cosmate.entity.AiTokenSubscriptionPlan;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.AiTokenSubscriptionPlanRepository;
import com.cosmate.service.AiTokenPlanService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiTokenPlanServiceImpl implements AiTokenPlanService {

    private static final Logger logger = LoggerFactory.getLogger(AiTokenPlanServiceImpl.class);

    private final AiTokenSubscriptionPlanRepository repository;

    private void requireStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = false;
        if (auth != null && auth.isAuthenticated()) {
            for (GrantedAuthority ga : auth.getAuthorities()) {
                String a = ga.getAuthority();
                if ("ROLE_STAFF".equals(a) || "ROLE_ADMIN".equals(a) || "ROLE_SUPERADMIN".equals(a)) { allowed = true; break; }
            }
        }
        if (!allowed) throw new AppException(ErrorCode.FORBIDDEN);
    }

    @Override
    @Transactional
    public AiTokenPlanResponse create(AiTokenPlanRequest req) {
        requireStaff();
        AiTokenSubscriptionPlan e = AiTokenSubscriptionPlan.builder()
                .name(req.getName())
                .description(req.getDescription())
                .price(req.getPrice())
                .numberOfToken(req.getNumberOfToken() == null ? 0 : req.getNumberOfToken())
                .isActive(true)
                .build();
        AiTokenSubscriptionPlan saved = repository.save(e);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AiTokenPlanResponse update(Integer id, AiTokenPlanRequest req) {
        requireStaff();
        AiTokenSubscriptionPlan e = repository.findById(id).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        if (req.getName() != null) e.setName(req.getName());
        if (req.getDescription() != null) e.setDescription(req.getDescription());
        if (req.getPrice() != null) e.setPrice(req.getPrice());
        if (req.getNumberOfToken() != null) e.setNumberOfToken(req.getNumberOfToken());
        AiTokenSubscriptionPlan saved = repository.save(e);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deactivate(Integer id) {
        requireStaff();
        AiTokenSubscriptionPlan e = repository.findById(id).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        e.setIsActive(false);
        repository.save(e);
    }

    @Override
    public List<AiTokenPlanResponse> getAll() {
        return repository.findByIsActiveTrue().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public AiTokenPlanResponse getById(Integer id) {
        AiTokenSubscriptionPlan e = repository.findById(id).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        if (e.getIsActive() == null || !e.getIsActive()) throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        return toResponse(e);
    }

    private AiTokenPlanResponse toResponse(AiTokenSubscriptionPlan e) {
        return AiTokenPlanResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .price(e.getPrice())
                .numberOfToken(e.getNumberOfToken())
                .isActive(e.getIsActive())
                .build();
    }
}


