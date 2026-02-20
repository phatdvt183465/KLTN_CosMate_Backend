package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.entity.*;
import com.cosmate.repository.OrderRepository;
import com.cosmate.service.DisputeService;
import com.cosmate.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.cosmate.dto.request.ResolveDisputeRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {
    private final DisputeService disputeService;
    private final OrderRepository orderRepository;
    private final ProviderService providerService;
    private final com.cosmate.repository.ProviderRepository providerRepository;
    private final com.cosmate.service.WalletService walletService;
    private final com.cosmate.repository.OrderDetailRepository orderDetailRepository;

    // helper to extract current authenticated user id
    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
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

    private boolean hasAdminStaffRole(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> {
            String at = a.getAuthority();
            return "ROLE_ADMIN".equals(at) || "ROLE_STAFF".equals(at) || "ROLE_SUPERADMIN".equals(at)
                    || "ADMIN".equals(at) || "STAFF".equals(at) || "SUPERADMIN".equals(at);
        });
    }

    // Provider or cosplayer opens dispute on an order
    @PostMapping
    public ApiResponse<Dispute> openDispute(@RequestParam Integer orderId,
                                            @RequestBody(required = false) String reason) {
        try {
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Dispute>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();

            // ensure order exists
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) return ApiResponse.<Dispute>builder().code(404).message("Order not found").build();

            // only provider owner or cosplayer or admin/staff can create
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdminStaff = hasAdminStaffRole(auth);
            boolean isProviderOwner = false;
            try {
                // If order.providerId directly stores the user id (some historical data), accept that
                if (order.getProviderId() != null && order.getProviderId().equals(currentUserId)) {
                    isProviderOwner = true;
                } else {
                    java.util.Optional<com.cosmate.entity.Provider> provOpt = providerRepository.findByUserId(currentUserId);
                    if (provOpt.isPresent()) {
                        Provider p = provOpt.get();
                        if (p.getId() != null && p.getId().equals(order.getProviderId())) isProviderOwner = true;
                      }
                }
            } catch (Exception ex) { }

            boolean isCosplayer = currentUserId.equals(order.getCosplayerId());
            if (!isAdminStaff && !isProviderOwner && !isCosplayer) return ApiResponse.<Dispute>builder().code(403).message("Không có quyền tạo khiếu nại cho đơn hàng này").build();

            Dispute d = disputeService.createDispute(currentUserId, orderId, reason);
            return ApiResponse.<Dispute>builder().result(d).message("Dispute created").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<Dispute>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<Dispute>builder().code(500).message("Failed to create dispute: " + ex.getMessage()).build();
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Dispute> getDispute(@PathVariable Integer id) {
        Optional<Dispute> dopt = disputeService.getById(id);
        if (dopt.isEmpty()) return ApiResponse.<Dispute>builder().code(404).message("Dispute not found").build();
        Dispute d = dopt.get();

        // authorize: opener, order provider, or staff/admin
        Integer currentUserId = getCurrentUserId();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdminStaff = hasAdminStaffRole(auth);
        boolean isProviderOwner = false;
        try {
            Order od = d.getOrder();
            if (od != null && od.getProviderId() != null && od.getProviderId().equals(currentUserId)) {
                isProviderOwner = true;
            } else {
                java.util.Optional<com.cosmate.entity.Provider> provOpt = providerRepository.findByUserId(currentUserId);
                if (provOpt.isPresent()) {
                    Provider p = provOpt.get();
                    if (p.getId() != null && od != null && p.getId().equals(od.getProviderId())) isProviderOwner = true;
                }
            }
        } catch (Exception ex) { }
        boolean isOpener = currentUserId != null && currentUserId.equals(d.getCreatedByUserId());
        if (!isAdminStaff && !isProviderOwner && !isOpener) return ApiResponse.<Dispute>builder().code(403).message("Không có quyền xem khiếu nại này").build();

        return ApiResponse.<Dispute>builder().result(d).build();
    }

    // staff resolves dispute
    @PostMapping("/{id}/resolve")
    public ApiResponse<DisputeResult> resolveDispute(@PathVariable Integer id, @Valid @RequestBody ResolveDisputeRequest req) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<DisputeResult>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            if (!hasAdminStaffRole(auth)) return ApiResponse.<DisputeResult>builder().code(403).message("Chỉ staff hoặc admin mới có thể xử lý khiếu nại").build();

            // validate request: require either penaltyAmount > 0 or penaltyPercent > 0 (or both); if both provided, percent takes precedence
            boolean hasAmount = req.getPenaltyAmount() != null && req.getPenaltyAmount().compareTo(java.math.BigDecimal.ZERO) > 0;
            boolean hasPercent = req.getPenaltyPercent() != null && req.getPenaltyPercent().compareTo(java.math.BigDecimal.ZERO) > 0;
            if (!hasAmount && !hasPercent) {
                return ApiResponse.<DisputeResult>builder().code(400).message("penaltyAmount or penaltyPercent is required and must be > 0").build();
            }

            // build a DisputeResult entity to pass into service
            DisputeResult dr = new DisputeResult();
            dr.setResult(req.getResult());
            dr.setPenaltyAmount(hasAmount ? req.getPenaltyAmount() : null);
            dr.setPenaltyPercent(hasPercent ? req.getPenaltyPercent() : null);

            DisputeResult res = disputeService.resolveDispute(currentUserId, id, dr);
            return ApiResponse.<DisputeResult>builder().result(res).message("Dispute resolved").build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<DisputeResult>builder().code(400).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<DisputeResult>builder().code(500).message("Failed to resolve dispute: " + ex.getMessage()).build();
        }
    }

    // list disputes by status (staff) or by user
    @GetMapping
    public ApiResponse<List<Dispute>> listDisputes(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Integer userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Integer currentUserId = getCurrentUserId();
        boolean isAdminStaff = hasAdminStaffRole(auth);

        try {
            if (userId != null) {
                // allow user to see own disputes or staff
                if (!isAdminStaff && (currentUserId == null || !currentUserId.equals(userId))) return ApiResponse.<List<Dispute>>builder().code(403).message("Không có quyền xem danh sách khiếu nại của user này").build();
                return ApiResponse.<List<Dispute>>builder().result(disputeService.listByUser(userId)).build();
            }

            if (status != null) {
                if (!isAdminStaff) return ApiResponse.<List<Dispute>>builder().code(403).message("Chỉ staff có thể lọc theo trạng thái").build();
                return ApiResponse.<List<Dispute>>builder().result(disputeService.listByStatus(status)).build();
            }

            // default: staff only list all recent
            if (!isAdminStaff) return ApiResponse.<List<Dispute>>builder().code(403).message("Không có quyền xem danh sách khiếu nại").build();
            return ApiResponse.<List<Dispute>>builder().result(disputeService.listByStatus("OPEN")).build();
        } catch (Exception ex) {
            return ApiResponse.<List<Dispute>>builder().code(500).message("Failed to list disputes: " + ex.getMessage()).build();
        }
    }

    // Debug endpoint for staff: returns order, deposit total, wallet balances and recent transactions for cosplayer/provider
    @GetMapping("/{id}/debug")
    public ApiResponse<java.util.Map<String, Object>> debugDispute(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null || !hasAdminStaffRole(auth)) return ApiResponse.<java.util.Map<String,Object>>builder().code(403).message("Forbidden").build();

        Optional<Dispute> dopt = disputeService.getById(id);
        if (dopt.isEmpty()) return ApiResponse.<java.util.Map<String,Object>>builder().code(404).message("Dispute not found").build();
        Dispute d = dopt.get();
        Order order = d.getOrder();
        java.util.Map<String,Object> res = new java.util.HashMap<>();
        res.put("dispute", d);
        res.put("order", order);

        // compute deposit total
        BigDecimal depositTotal = BigDecimal.ZERO;
        java.util.List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
        for (OrderDetail od : details) {
            if (od.getDepositAmount() != null) depositTotal = depositTotal.add(od.getDepositAmount());
        }
        res.put("depositTotal", depositTotal);

        // provider user id
        Integer providerUserId = null;
        if (order.getProviderId() != null) {
            java.util.Optional<com.cosmate.entity.Provider> provOpt = providerRepository.findById(order.getProviderId());
            if (provOpt.isPresent()) providerUserId = provOpt.get().getUserId();
        }
        res.put("providerUserId", providerUserId);

        // provider wallet
        com.cosmate.entity.Wallet providerWallet = null;
        if (providerUserId != null) {
            providerWallet = walletService.getByUserId(providerUserId).orElse(null);
        }
        res.put("providerWallet", providerWallet);

        // cosplayer wallet
        com.cosmate.entity.Wallet cosWallet = walletService.getByUserId(order.getCosplayerId()).orElse(null);
        res.put("cosplayerWallet", cosWallet);

        // recent transactions (limit 10)
        java.util.List<com.cosmate.entity.Transaction> providerTxs = providerWallet == null ? java.util.Collections.emptyList() : walletService.getTransactionsForWallet(providerWallet);
        java.util.List<com.cosmate.entity.Transaction> cosTxs = cosWallet == null ? java.util.Collections.emptyList() : walletService.getTransactionsForWallet(cosWallet);
        res.put("providerTransactions", providerTxs.size() > 10 ? providerTxs.subList(0,10) : providerTxs);
        res.put("cosplayerTransactions", cosTxs.size() > 10 ? cosTxs.subList(0,10) : cosTxs);

        return ApiResponse.<java.util.Map<String,Object>>builder().result(res).build();
    }
}
