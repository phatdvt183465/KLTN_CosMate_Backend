package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.service.MomoService;
import com.cosmate.service.SubscriptionService;
import com.cosmate.service.VnPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final VnPayService vnPayService;
    private final SubscriptionService subscriptionService;
    private final MomoService momoService;

    @PostMapping("/api/vnpay/create")
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

    @PostMapping("/api/momo/create")
    public ResponseEntity<ApiResponse<Map<String, String>>> createMomoPayment(@RequestParam Integer userId,
                                                                               @RequestParam BigDecimal amount,
                                                                               @RequestParam(required = false) String returnUrl) {
        ApiResponse<Map<String, String>> api = new ApiResponse<>();
        try {
            String url = momoService.createPaymentUrl(userId, amount, returnUrl);
            Map<String, String> res = new HashMap<>();
            res.put("paymentUrl", url);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(res);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            api.setCode(9999);
            api.setMessage("Error creating Momo payment: " + e.getMessage());
            return ResponseEntity.internalServerError().body(api);
        }
    }

    @PostMapping("/api/create")
    public ResponseEntity<ApiResponse<Map<String, String>>> createPaymentWithMethod(@RequestParam Integer userId,
                                                                                     @RequestParam BigDecimal amount,
                                                                                     @RequestParam String paymentMethod,
                                                                                     @RequestParam(required = false) String returnUrl,
                                                                                     @RequestParam(required = false) Integer transactionId) {
        ApiResponse<Map<String, String>> api = new ApiResponse<>();
        try {
            String url;
            if (paymentMethod != null && paymentMethod.equalsIgnoreCase("momo")) {
                if (transactionId != null) url = momoService.createPaymentUrlForTransaction(userId, amount, returnUrl, transactionId);
                else url = momoService.createPaymentUrl(userId, amount, returnUrl);
            } else { // default VNPay
                if (transactionId != null) url = vnPayService.createPaymentUrlForTransaction(userId, amount, returnUrl, transactionId);
                else url = vnPayService.createPaymentUrl(userId, amount, returnUrl);
            }
            Map<String, String> res = new HashMap<>();
            res.put("paymentUrl", url);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(res);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            api.setCode(9999);
            api.setMessage("Error creating payment: " + e.getMessage());
            return ResponseEntity.internalServerError().body(api);
        }
    }

    @GetMapping("/api/vnpay/return")
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

    @PostMapping("/api/momo/notify")
    public ResponseEntity<ApiResponse<Map<String, String>>> momoNotify(@RequestParam Map<String, String> allParams) {
        ApiResponse<Map<String, String>> api = new ApiResponse<>();
        try {
            Map<String, String> result = momoService.handleNotification(allParams);
            // if OK and SUB transaction, finalize subscription
            if ("OK".equals(result.get("status")) && result.containsKey("transactionId")) {
                try {
                    Integer txnId = Integer.valueOf(result.get("transactionId"));
                    // find transaction by id and if it's a subscription, finalize
                    subscriptionService.finalizeSubscriptionPayment(txnId);
                } catch (Exception e) {
                    // ignore
                }
            }
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(result);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            api.setCode(9999);
            api.setMessage("Error handling Momo notification: " + e.getMessage());
            return ResponseEntity.internalServerError().body(api);
        }
    }

    @GetMapping("/api/momo/return")
    public ResponseEntity<ApiResponse<Map<String, String>>> momoReturn(@RequestParam Map<String, String> allParams) {
        ApiResponse<Map<String, String>> api = new ApiResponse<>();
        try {
            Map<String, String> result = momoService.handleNotification(allParams);
            if ("OK".equals(result.get("status"))) {
                String orderId = allParams.get("orderId");
                if (orderId != null && orderId.startsWith("SUB")) {
                    String idPart = orderId.substring("SUB".length());
                    try {
                        Integer txnId = Integer.valueOf(idPart);
                        subscriptionService.finalizeSubscriptionPayment(txnId);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(result);
            return ResponseEntity.ok(api);
        } catch (Exception e) {
            api.setCode(9999);
            api.setMessage("Error handling Momo return: " + e.getMessage());
            return ResponseEntity.internalServerError().body(api);
        }
    }
}
