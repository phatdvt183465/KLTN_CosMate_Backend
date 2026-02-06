package com.cosmate.service.impl;

import com.cosmate.entity.*;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.repository.ProviderSubscriptionRepository;
import com.cosmate.repository.SubscriptionPlanRepository;
import com.cosmate.repository.TransactionRepository;
import com.cosmate.service.SubscriptionService;
import com.cosmate.service.VnPayService;
import com.cosmate.service.WalletService;
import com.cosmate.dto.request.PaymentMethod;
import com.cosmate.service.MomoService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final ProviderRepository providerRepository;
    private final ProviderSubscriptionRepository subscriptionRepository;
    private final WalletService walletService;
    private final VnPayService vnPayService;
    private final MomoService momoService;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public SubscriptionPlan createPlan(SubscriptionPlan plan) {
        return planRepository.save(plan);
    }

    @Override
    @Transactional
    public SubscriptionPlan updatePlan(Integer id, SubscriptionPlan plan) {
        SubscriptionPlan existing = planRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        if (plan.getName() != null) existing.setName(plan.getName());
        if (plan.getBillingCycle() != null) existing.setBillingCycle(plan.getBillingCycle());
        if (plan.getCycleMonths() != null) existing.setCycleMonths(plan.getCycleMonths());
        if (plan.getPrice() != null) existing.setPrice(plan.getPrice());
        if (plan.getIsActive() != null) existing.setIsActive(plan.getIsActive());
        if (plan.getDescription() != null) existing.setDescription(plan.getDescription());
        return planRepository.save(existing);
    }

    @Override
    public List<SubscriptionPlan> listPlans() {
        return planRepository.findAll();
    }

    @Override
    @Transactional
    public String initiateProviderSubscription(Integer providerUserId, Integer planId, String returnUrl, PaymentMethod paymentMethod) throws Exception {
        // find provider by user id
        Provider provider = providerRepository.findByUserId(providerUserId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        SubscriptionPlan plan = planRepository.findById(planId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));

        // Determine start date: if provider has current active subscription, start = end_date + 1 day; else start = now
        Optional<ProviderSubscription> lastOpt = subscriptionRepository.findFirstByProviderOrderByEndDateDesc(provider);
        LocalDateTime start = LocalDateTime.now(ZoneId.systemDefault());
        if (lastOpt.isPresent() && lastOpt.get().getEndDate() != null && lastOpt.get().getEndDate().isAfter(LocalDateTime.now(ZoneId.systemDefault()))) {
            // start exactly when the previous subscription ends (no gap)
            start = lastOpt.get().getEndDate();
        }
        LocalDateTime end = start.plusMonths(plan.getCycleMonths());

        ProviderSubscription ps = ProviderSubscription.builder()
                .provider(provider)
                .subscriptionPlan(plan)
                .name(plan.getName())
                .duration(String.valueOf(plan.getCycleMonths()))
                .price(plan.getPrice())
                .startDate(start)
                .endDate(end)
                .status("PENDING")
                .build();
        ps = subscriptionRepository.save(ps);

        // Create pending transaction via walletService (use createPaymentUrl flow: create a pending Transaction in VnPayService)
        // We'll create a pending transaction here referencing the subscription by description
        // Use walletService.createForUser to get wallet and then create a Transaction manually via transactionRepository
        com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(providerUserId).build();
        var wallet = walletService.createForUser(u);

        Transaction t = Transaction.builder()
                .wallet(wallet)
                .amount(plan.getPrice())
                .type("SUBSCRIPTION")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        t = transactionRepository.save(t);

        // Link transaction to subscription
        ps.setTransaction(t);
        subscriptionRepository.save(ps);

        // Decide payment provider; default to VNPAY for backward compatibility
        PaymentMethod pm = paymentMethod == null ? PaymentMethod.VNPAY : paymentMethod;

        String payUrl;
        if (pm == PaymentMethod.VNPAY) {
            payUrl = vnPayService.createPaymentUrlForTransaction(wallet.getUser().getId(), plan.getPrice(), returnUrl, t.getId());
        } else if (pm == PaymentMethod.MOMO) {
            payUrl = momoService.createPaymentUrlForTransaction(wallet.getUser().getId(), plan.getPrice(), returnUrl, t.getId());
        } else {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        return payUrl;
    }

    @Override
    @Transactional
    public ProviderSubscription finalizeSubscriptionPayment(Integer transactionId) throws Exception {
        // Find transaction
        Transaction t = transactionRepository.findById(transactionId).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        if (!"COMPLETED".equalsIgnoreCase(t.getStatus())) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        // Find subscription by transaction
        Optional<ProviderSubscription> opt = subscriptionRepository.findByTransaction(t);
        if (opt.isEmpty()) throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        ProviderSubscription ps = opt.get();
        // mark subscription active
        ps.setStatus("ACTIVE");
        // If startDate is null, set to now
        if (ps.getStartDate() == null) ps.setStartDate(LocalDateTime.now());
        if (ps.getEndDate() == null) ps.setEndDate(ps.getStartDate().plusMonths(ps.getSubscriptionPlan().getCycleMonths()));
        return subscriptionRepository.save(ps);
    }

    // New methods
    @Override
    public List<ProviderSubscription> listByProviderUserId(Integer providerUserId) {
        Provider provider = providerRepository.findByUserId(providerUserId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return subscriptionRepository.findByProviderOrderByStartDateDesc(provider);
    }

    @Override
    public List<ProviderSubscription> listAllSubscriptions() {
        return subscriptionRepository.findAll();
    }
}
