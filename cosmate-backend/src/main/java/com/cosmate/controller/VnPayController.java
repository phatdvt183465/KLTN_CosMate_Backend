package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.service.SubscriptionService;
import com.cosmate.service.VnPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/vnpay")
@RequiredArgsConstructor
public class VnPayController {

    private final VnPayService vnPayService;
    private final SubscriptionService subscriptionService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Map<String, String>>> createPayment(@RequestParam Integer userId,
                                                                           @RequestParam BigDecimal amount,
                                                                           @RequestParam String returnUrl) {
        ApiResponse<Map<String, String>> api = new ApiResponse<>();
        try {
            String url = vnPayService.createPaymentUrl(userId, amount, returnUrl);
            Map<String, String> res = new HashMap<>();
            res.put("paymentUrl", url);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(res);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            api.setCode(9999);
            api.setMessage("Error creating VNPay URL: " + e.getMessage());
            return ResponseEntity.internalServerError().body(api);
        }
    }

    @GetMapping("/return")
    public ResponseEntity<ApiResponse<Map<String, String>>> vnpReturn(@RequestParam Map<String, String> allParams) {
        ApiResponse<Map<String, String>> api = new ApiResponse<>();
        try {
            Map<String, String> result = vnPayService.handleReturn(allParams);
            // If transaction is SUB and completed, activate subscription
            String status = result.get("status");
            if ("OK".equals(status)) {
                String txnRef = allParams.get("vnp_TxnRef");
                if (txnRef != null && txnRef.startsWith("SUB")) {
                    String idPart = txnRef.substring("SUB".length());
                    try {
                        Integer txnId = Integer.valueOf(idPart);
                        subscriptionService.finalizeSubscriptionPayment(txnId);
                    } catch (Exception e) {
                        // log and continue; return will still be OK to VNPay
                    }
                }
            }
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(result);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            api.setCode(9999);
            api.setMessage("Error handling return: " + e.getMessage());
            return ResponseEntity.internalServerError().body(api);
        }
    }
}
