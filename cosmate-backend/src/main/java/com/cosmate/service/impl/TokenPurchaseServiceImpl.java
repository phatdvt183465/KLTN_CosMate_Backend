package com.cosmate.service.impl;

import com.cosmate.dto.response.TokenPurchaseResponse;
import com.cosmate.entity.AiTokenSubscriptionPlan;
import com.cosmate.entity.TokenPurchaseHistory;
import com.cosmate.entity.Transaction;
import com.cosmate.entity.User;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.AiTokenSubscriptionPlanRepository;
import com.cosmate.repository.TokenPurchaseHistoryRepository;
import com.cosmate.repository.TransactionRepository;
import com.cosmate.service.TokenPurchaseService;
import com.cosmate.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TokenPurchaseServiceImpl implements TokenPurchaseService {

    private static final Logger logger = LoggerFactory.getLogger(TokenPurchaseServiceImpl.class);

    private final AiTokenSubscriptionPlanRepository planRepository;
    private final TokenPurchaseHistoryRepository historyRepository;
    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final com.cosmate.service.VnPayService vnPayService; // for creating VNPay URLs
    private final com.cosmate.service.MomoService momoService;
    private final com.cosmate.repository.UserRepository userRepository;

    @Override
    @Transactional
    public String initiatePurchase(Integer userId, Integer subscriptionPlanId, String paymentMethod, String returnUrl) throws Exception {
        AiTokenSubscriptionPlan plan = planRepository.findById(subscriptionPlanId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));

        // Prepare wallet object
        User u = User.builder().id(userId).build();
        var wallet = walletService.createForUser(u);

        // Payment flows: wallet -> immediate debit; momo/vnpay -> create pending tx and redirect URL
        if (paymentMethod != null && (paymentMethod.equalsIgnoreCase("wallet") || paymentMethod.equalsIgnoreCase("wallet_balance"))) {
            // immediate debit from wallet -> walletService will create and persist Transaction
            try {
                var savedTx = walletService.debit(wallet, plan.getPrice(), "TOKEN_PURCHASE", "TOKEN_PURCHASE", "WALLET", null);
                // create history linked to this completed transaction
                TokenPurchaseHistory h = TokenPurchaseHistory.builder()
                        .user(u)
                        .subscriptionPlan(plan)
                        .priceAtPurchase(plan.getPrice())
                        .tokensAdded(plan.getNumberOfToken())
                        .transaction(savedTx)
                        .status("PENDING")
                        .build();
                h = historyRepository.save(h);
                // finalize immediately (savedTx should be COMPLETED)
                finalizePurchase(savedTx.getId());
                return null;
            } catch (Exception ex) {
                logger.error("Wallet debit failed for user {}: {}", userId, ex.getMessage(), ex);
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
            }
        }

        // For offsite payments (VNPay/Momo) create a pending transaction first
        Transaction t = Transaction.builder()
                .wallet(wallet)
                .amount(plan.getPrice())
                .type("SUBSCRIPTION_TOKEN")
                .status("PENDING")
                .paymentMethod(paymentMethod == null ? null : paymentMethod.toUpperCase())
                .createdAt(LocalDateTime.now(ZoneId.systemDefault()))
                .build();
        t = transactionRepository.save(t);

        // create history record (pending) with transaction set (DB requires transaction_id not null)
        TokenPurchaseHistory h = TokenPurchaseHistory.builder()
                .user(u)
                .subscriptionPlan(plan)
                .priceAtPurchase(plan.getPrice())
                .tokensAdded(plan.getNumberOfToken())
                .transaction(t)
                .status("PENDING")
                .build();
        h = historyRepository.save(h);

        if (paymentMethod == null || paymentMethod.equalsIgnoreCase("vnpay")) {
            String r = (returnUrl == null || returnUrl.isEmpty()) ? "/api/payment/api/vnpay/return" : returnUrl;
            return vnPayService.createPaymentUrlForTransaction(userId, plan.getPrice(), r, t.getId());
        } else if (paymentMethod.equalsIgnoreCase("momo")) {
            String r = (returnUrl == null || returnUrl.isEmpty()) ? "/api/payment/api/momo/return" : returnUrl;
            return momoService.createPaymentUrlForTransaction(userId, plan.getPrice(), r, t.getId());
        } else {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
    }

    @Override
    @Transactional
    public void finalizePurchase(Integer transactionId) {
        Transaction t = transactionRepository.findById(transactionId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        if (!"COMPLETED".equalsIgnoreCase(t.getStatus()) && !"COMPLETED".equalsIgnoreCase(t.getStatus())) {
            // If transaction isn't completed yet, ignore
            logger.warn("Attempt to finalize token purchase for txn {} which is not completed: status={}", transactionId, t.getStatus());
            return;
        }
        // find history by transaction
        List<TokenPurchaseHistory> hs = historyRepository.findAll().stream().filter(h -> h.getTransaction() != null && h.getTransaction().getId().equals(transactionId)).collect(Collectors.toList());
        if (hs.isEmpty()) {
            logger.warn("No TokenPurchaseHistory found for transaction {}", transactionId);
            return;
        }
        TokenPurchaseHistory h = hs.get(0);

        if ("SUCCESS".equalsIgnoreCase(h.getStatus()) || "SUCCESS".equalsIgnoreCase(h.getStatus())) return;

        // add tokens to user
        Integer userId = t.getWallet() != null && t.getWallet().getUser() != null ? t.getWallet().getUser().getId() : (h.getUser() == null ? null : h.getUser().getId());
        if (userId == null) throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        com.cosmate.entity.User u = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Integer cur = u.getNumberOfToken() == null ? 0 : u.getNumberOfToken();
        u.setNumberOfToken(cur + (h.getTokensAdded() == null ? 0 : h.getTokensAdded()));
        userRepository.save(u);

        h.setStatus("SUCCESS");
        historyRepository.save(h);
    }

    @Override
    public TokenPurchaseResponse getById(Integer id, Integer requesterUserId) {
        TokenPurchaseHistory h = historyRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        // check permission: owner or staff+
        if (requesterUserId == null || (!requesterUserId.equals(h.getUser().getId()))) {
            // check authorities
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean isStaff = false;
            if (auth != null && auth.isAuthenticated()) {
                for (org.springframework.security.core.GrantedAuthority ga : auth.getAuthorities()) {
                    if ("ROLE_STAFF".equals(ga.getAuthority()) || "ROLE_ADMIN".equals(ga.getAuthority()) || "ROLE_SUPERADMIN".equals(ga.getAuthority())) { isStaff = true; break; }
                }
            }
            if (!isStaff) throw new AppException(ErrorCode.FORBIDDEN);
        }
        return toResponse(h);
    }

    @Override
    public List<TokenPurchaseResponse> getAll() {
        // staff only; controller will guard
        return historyRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<TokenPurchaseResponse> getByUser(Integer userId) {
        com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(userId).build();
        return historyRepository.findByUser(u).stream().map(this::toResponse).collect(Collectors.toList());
    }

    private TokenPurchaseResponse toResponse(TokenPurchaseHistory h) {
        return TokenPurchaseResponse.builder()
                .id(h.getId())
                .userId(h.getUser() == null ? null : h.getUser().getId())
                .subscriptionId(h.getSubscriptionPlan() == null ? null : h.getSubscriptionPlan().getId())
                .transactionId(h.getTransaction() == null ? null : h.getTransaction().getId())
                .priceAtPurchase(h.getPriceAtPurchase())
                .tokensAdded(h.getTokensAdded())
                .purchaseDate(h.getPurchaseDate())
                .status(h.getStatus())
                .build();
    }

    // Scheduled job: every minute check pending histories older than 20 minutes and mark Failed
    @Scheduled(fixedRate = 60 * 1000)
    @Transactional
    public void failOldPending() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneId.systemDefault()).minusMinutes(20);
        List<TokenPurchaseHistory> old = historyRepository.findByStatusAndPurchaseDateBefore("PENDING", cutoff);
        for (TokenPurchaseHistory h : old) {
            h.setStatus("FAILED");
            historyRepository.save(h);
            logger.info("Marked token purchase history id={} as FAILED due to timeout", h.getId());
        }
    }
}


