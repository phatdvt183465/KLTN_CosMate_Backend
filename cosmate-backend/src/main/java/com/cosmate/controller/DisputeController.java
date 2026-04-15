package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.entity.*;
import com.cosmate.service.DisputeService;
import com.cosmate.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import com.cosmate.dto.request.ResolveDisputeRequest;
import com.cosmate.service.FirebaseStorageService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {
    private final DisputeService disputeService;

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
                                            @RequestPart(required = false) String reason,
                                            @RequestPart(name = "files", required = false) org.springframework.web.multipart.MultipartFile[] files) {
        try {
            Integer currentUserId = getCurrentUserId();
            if (currentUserId == null) return ApiResponse.<Dispute>builder().code(401).message("Chưa xác thực - Vui lòng đăng nhập").build();
            // upload files via service layer (if any) and collect urls
            java.util.List<String> images = null;
            try {
                images = disputeService.uploadFilesForDispute(orderId, files);
            } catch (Exception ex) {
                images = null;
            }

            // Delegate validation and creation to the service layer
            Dispute d = disputeService.createDispute(currentUserId, orderId, reason, images);
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

        Integer currentUserId = getCurrentUserId();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdminStaff = hasAdminStaffRole(auth);
        // allow staff/admin
        if (!isAdminStaff) {
            boolean canView = disputeService.canViewDispute(id, currentUserId);
            if (!canView) return ApiResponse.<Dispute>builder().code(403).message("Không có quyền xem khiếu nại này").build();
        }

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

    // list disputes by order id (order owner or provider owner or staff)
    @GetMapping("/order/{orderId}")
    public ApiResponse<List<Dispute>> getDisputesByOrder(@PathVariable Integer orderId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Integer currentUserId = getCurrentUserId();
        boolean isAdminStaff = hasAdminStaffRole(auth);

        try {
            if (!isAdminStaff) {
                if (currentUserId == null || !disputeService.canViewOrderDisputes(orderId, currentUserId)) {
                    return ApiResponse.<List<Dispute>>builder().code(403).message("Không có quyền xem khiếu nại cho đơn này").build();
                }
            }
            java.util.List<Dispute> disputes = disputeService.listByOrder(orderId);
            return ApiResponse.<List<Dispute>>builder().result(disputes).build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<List<Dispute>>builder().code(404).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<List<Dispute>>builder().code(500).message("Failed to get disputes by order: " + ex.getMessage()).build();
        }
    }

    // Debug endpoint for staff: returns order, deposit total, wallet balances and recent transactions for cosplayer/provider
    @GetMapping("/{id}/debug")
    public ApiResponse<java.util.Map<String, Object>> debugDispute(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null || !hasAdminStaffRole(auth)) return ApiResponse.<java.util.Map<String,Object>>builder().code(403).message("Forbidden").build();
        try {
            java.util.Map<String,Object> res = disputeService.debugDispute(id);
            return ApiResponse.<java.util.Map<String,Object>>builder().result(res).build();
        } catch (IllegalArgumentException ex) {
            return ApiResponse.<java.util.Map<String,Object>>builder().code(404).message(ex.getMessage()).build();
        } catch (Exception ex) {
            return ApiResponse.<java.util.Map<String,Object>>builder().code(500).message("Failed to debug dispute: " + ex.getMessage()).build();
        }
    }
}
