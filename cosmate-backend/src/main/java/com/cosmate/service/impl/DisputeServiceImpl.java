package com.cosmate.service.impl;

import com.cosmate.entity.*;
import com.cosmate.repository.*;
import com.cosmate.service.DisputeService;
import com.cosmate.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class DisputeServiceImpl implements DisputeService {
    private final DisputeRepository disputeRepository;
    private final DisputeResultRepository disputeResultRepository;
    private final OrderRepository orderRepository;
    private final OrderTrackingRepository orderTrackingRepository;
    private final WalletService walletService;
    private final OrderDetailRepository orderDetailRepository;
    private final com.cosmate.repository.ProviderRepository providerRepository;

    public DisputeServiceImpl(DisputeRepository disputeRepository,
                              DisputeResultRepository disputeResultRepository,
                              OrderRepository orderRepository,
                              OrderTrackingRepository orderTrackingRepository,
                              WalletService walletService,
                              OrderDetailRepository orderDetailRepository,
                              com.cosmate.repository.ProviderRepository providerRepository) {
        this.disputeRepository = disputeRepository;
        this.disputeResultRepository = disputeResultRepository;
        this.orderRepository = orderRepository;
        this.orderTrackingRepository = orderTrackingRepository;
        this.walletService = walletService;
        this.orderDetailRepository = orderDetailRepository;
        this.providerRepository = providerRepository;
    }

    @Override
    public Dispute createDispute(Integer openerUserId, Integer orderId, String reason) throws Exception {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) throw new IllegalArgumentException("Order not found");

        // only provider owner or cosplayer or admin/staff are allowed to create dispute. We check caller at controller level; here we allow creation if caller is either cosplayer or provider owner
        boolean byCosplayer = openerUserId != null && openerUserId.equals(order.getCosplayerId());
        boolean byProvider = false;
        if (openerUserId != null) {
            try {
                // if order.providerId directly stores the user id
                if (order.getProviderId() != null && order.getProviderId().equals(openerUserId)) {
                    byProvider = true;
                } else {
                    java.util.Optional<com.cosmate.entity.Provider> provOpt = providerRepository.findByUserId(openerUserId);
                    if (provOpt.isPresent()) {
                        com.cosmate.entity.Provider prov = provOpt.get();
                        if (prov.getId() != null && prov.getId().equals(order.getProviderId())) byProvider = true;
                        if (prov.getUserId() != null && prov.getUserId().equals(order.getProviderId())) byProvider = true;
                    }
                }
            } catch (Exception ex) {
                byProvider = false;
            }
        }
        if (!byCosplayer && !byProvider) {
            // allow but still create? safer to reject
            throw new IllegalArgumentException("Only order owner or provider may create a dispute");
        }

        Dispute d = Dispute.builder()
                .order(order)
                .createdByUserId(openerUserId)
                .reason(reason)
                .status("OPEN")
                .build();
        d = disputeRepository.save(d);

        // set order status to DISPUTE
        order.setStatus("DISPUTE");
        orderRepository.save(order);

        // add tracking entry
        OrderTracking ot = OrderTracking.builder()
                .order(order)
                .trackingCode(null)
                .trackingStatus("DISPUTE_OPENED")
                .stage("DISPUTE")
                .build();
        orderTrackingRepository.save(ot);

        return d;
    }

    @Override
    public Optional<Dispute> getById(Integer id) {
        return disputeRepository.findById(id);
    }

    @Override
    public List<Dispute> listByOrder(Integer orderId) {
        return disputeRepository.findByOrderId(orderId);
    }

    @Override
    public List<Dispute> listByUser(Integer userId) {
        return disputeRepository.findByCreatedByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<Dispute> listByStatus(String status) {
        return disputeRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DisputeResult resolveDispute(Integer resolverUserId, Integer disputeId, DisputeResult result) throws Exception {
        Dispute d = disputeRepository.findById(disputeId).orElse(null);
        if (d == null) throw new IllegalArgumentException("Dispute not found");

        if (d.getStatus() != null && "RESOLVED".equalsIgnoreCase(d.getStatus())) {
            throw new IllegalArgumentException("Dispute already resolved");
        }

        Order order = d.getOrder();
        if (order == null) throw new IllegalStateException("Related order not found");

        // compute total deposit for the order (sum of OrderDetail.depositAmount)
        BigDecimal depositTotal = BigDecimal.ZERO;
        try {
            java.util.List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
            for (OrderDetail od : details) {
                if (od.getDepositAmount() != null) depositTotal = depositTotal.add(od.getDepositAmount());
            }
        } catch (Exception ex) {
            // if any issue, keep depositTotal as zero
        }

        // determine penalty: if penaltyPercent provided (>0) use percent of depositTotal, otherwise use penaltyAmount
        BigDecimal penaltyAmount = result.getPenaltyAmount() == null ? BigDecimal.ZERO : result.getPenaltyAmount();
        BigDecimal penaltyPercent = result.getPenaltyPercent() == null ? BigDecimal.ZERO : result.getPenaltyPercent();

        BigDecimal appliedPenalty = BigDecimal.ZERO;
        if (penaltyPercent.compareTo(BigDecimal.ZERO) > 0) {
            // compute as depositTotal * percent / 100 with 2 decimal scale
            appliedPenalty = depositTotal.multiply(penaltyPercent).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        } else if (penaltyAmount.compareTo(BigDecimal.ZERO) > 0) {
            appliedPenalty = penaltyAmount;
        }

        // cap appliedPenalty to depositTotal (only penalize on deposit)
        if (appliedPenalty.compareTo(depositTotal) > 0) {
            appliedPenalty = depositTotal;
        }

        if (appliedPenalty.compareTo(BigDecimal.ZERO) > 0) {
            // get or create wallets for cosplayer and provider
            com.cosmate.entity.User cosUser = com.cosmate.entity.User.builder().id(order.getCosplayerId()).build();
            // fetch cosplayer wallet deterministically by user id
            com.cosmate.entity.Wallet cosWallet = walletService.getByUserId(order.getCosplayerId()).orElseGet(() -> walletService.createForUser(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build()));

            // resolve provider's user id from Provider entity (order.providerId references Provider.id)
            Integer providerUserId = null;
            if (order.getProviderId() != null) {
                try {
                    java.util.Optional<com.cosmate.entity.Provider> providerOpt = providerRepository.findById(order.getProviderId());
                    if (providerOpt.isPresent()) providerUserId = providerOpt.get().getUserId();
                } catch (Exception ex) {
                    providerUserId = null;
                }
            }
            com.cosmate.entity.Wallet provWallet;
            if (providerUserId != null) {
                final com.cosmate.entity.User provUserForCreate = com.cosmate.entity.User.builder().id(providerUserId).build();
                provWallet = walletService.getByUserId(providerUserId).orElseGet(() -> walletService.createForUser(provUserForCreate));
            } else {
                // fallback: create a wallet tied to a user with provider id (legacy)
                com.cosmate.entity.User provUser = com.cosmate.entity.User.builder().id(order.getProviderId()).build();
                provWallet = walletService.getByUser(provUser).orElseGet(() -> walletService.createForUser(provUser));
            }

            // Determine if dispute was created by provider
            boolean createdByProvider = false;
            try {
                Integer creatorUserId = d.getCreatedByUserId();
                if (creatorUserId != null) {
                    if (order.getProviderId() != null && order.getProviderId().equals(creatorUserId)) {
                        createdByProvider = true;
                    } else {
                        java.util.Optional<com.cosmate.entity.Provider> provOpt = providerRepository.findByUserId(creatorUserId);
                        if (provOpt.isPresent()) {
                            com.cosmate.entity.Provider provInfo = provOpt.get();
                            if (provInfo.getId() != null && provInfo.getId().equals(order.getProviderId())) createdByProvider = true;
                        }
                    }
                }
            } catch (Exception ex) {
                createdByProvider = false;
            }

            if (createdByProvider) {
                // Provider opened dispute: staff decision moves portion of deposit to provider, refund rest to cosplayer
                // depositTotal computed earlier
                BigDecimal refundRemaining = depositTotal.subtract(appliedPenalty);

                // Do NOT modify OrderDetail.depositAmount. Use depositTotal only as the basis for distribution.
                // Provider receives rent + appliedPenalty; refundRemaining goes back to cosplayer main balance
                BigDecimal orderTotal = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
                BigDecimal providerPayout = orderTotal.subtract(refundRemaining);

                if (providerPayout.compareTo(BigDecimal.ZERO) > 0) {
                    com.cosmate.entity.Wallet providerWalletEntity;
                    if (providerUserId != null) {
                        final com.cosmate.entity.User provUserForCreate = com.cosmate.entity.User.builder().id(providerUserId).build();
                        providerWalletEntity = walletService.getByUserId(providerUserId).orElseGet(() -> walletService.createForUser(provUserForCreate));
                    } else {
                        providerWalletEntity = provWallet;
                    }
                    // credit provider (walletService will update balance and create Transaction)
                    walletService.credit(providerWalletEntity, providerPayout, "Dispute payout to provider", "DISPUTE_PAYOUT:" + d.getId());
                }

                // Refund remaining deposit to cosplayer main balance (wallet)
                if (refundRemaining.compareTo(BigDecimal.ZERO) > 0) {
                    com.cosmate.entity.User cosUserForCreate = com.cosmate.entity.User.builder().id(order.getCosplayerId()).build();
                    com.cosmate.entity.Wallet cosWalletEntity = walletService.getByUserId(order.getCosplayerId()).orElseGet(() -> walletService.createForUser(cosUserForCreate));
                    walletService.credit(cosWalletEntity, refundRemaining, "Deposit refund", "DISPUTE_REFUND:" + d.getId());
                }

            } else {
                // Default flow: debit cosplayer main balance, credit provider main balance
                walletService.debit(cosWallet, appliedPenalty, "Dispute penalty", "DISPUTE_PENALTY:" + d.getId());
                walletService.credit(provWallet, appliedPenalty, "Dispute compensation received", "DISPUTE_COMP:" + d.getId());
            }
        }

        // store applied penalty for audit in result
        result.setPenaltyAmount(appliedPenalty);
        result.setPenaltyPercent(penaltyPercent);

        // persist dispute result
        result.setDispute(d);
        DisputeResult saved = disputeResultRepository.save(result);

        // update dispute
        d.setResult(saved);
        d.setStatus("RESOLVED");
        // set staff_id if desired to record who processed (table has staff_id in Disputes)
        d.setStaffId(resolverUserId);
        disputeRepository.save(d);

        // update order: after resolution, move to COMPLETED and record tracking
        order.setStatus("COMPLETED");
        orderRepository.save(order);

        OrderTracking ot = OrderTracking.builder()
                .order(order)
                .trackingCode(null)
                .trackingStatus("DISPUTE_RESOLVED")
                .stage("COMPLETED")
                .build();
        orderTrackingRepository.save(ot);

        return saved;
    }
}
