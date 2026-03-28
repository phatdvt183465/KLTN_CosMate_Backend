package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.service.MomoService;
import com.cosmate.service.SubscriptionService;
import com.cosmate.service.VnPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final VnPayService vnPayService;
    private final SubscriptionService subscriptionService;
    private final MomoService momoService;
    private final com.cosmate.service.PaymentService paymentService;

    // Read frontend URL from application.properties; fallback to http://localhost:5173 if not set
    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private String getFrontendBase() {
        if (frontendUrl == null || frontendUrl.isBlank()) return "http://localhost:5173";
        // remove trailing slash to make joining predictable
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

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
            Map<String, String> result = paymentService.processVnPayReturn(allParams);
            String status = result.get("status");
            StringBuilder redirect = new StringBuilder(getFrontendBase() + "/payment/result");
            redirect.append("?status=");
            String frontendStatus = "failed";
            if (status != null) {
                String s = status.trim();
                if (s.equalsIgnoreCase("OK") || s.equalsIgnoreCase("SUCCESS") || s.equals("00") || s.equals("0") || s.equalsIgnoreCase("ALREADY_DONE")) {
                    frontendStatus = "success";
                }
            }
            String encodedStatus = "";
            try { encodedStatus = URLEncoder.encode(frontendStatus, StandardCharsets.UTF_8); } catch (Exception ignore) {}
            redirect.append(encodedStatus);
            if (result.containsKey("transactionId")) redirect.append("&transactionId=").append(URLEncoder.encode(result.get("transactionId"), StandardCharsets.UTF_8));
            if (result.containsKey("orderId")) redirect.append("&orderId=").append(URLEncoder.encode(result.get("orderId"), StandardCharsets.UTF_8));
            URI location = URI.create(redirect.toString());
            return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
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
            Map<String, String> result = paymentService.processMomoNotification(allParams);
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
            Map<String, String> result = paymentService.processMomoReturn(allParams);
            String status = result.get("status");
            StringBuilder redirect = new StringBuilder(getFrontendBase() + "/payment/result");
            redirect.append("?status=");
            String frontendStatus = "failed";
            if (status != null) {
                String s = status.trim();
                if (s.equalsIgnoreCase("OK") || s.equalsIgnoreCase("SUCCESS") || s.equals("00") || s.equals("0") || s.equalsIgnoreCase("ALREADY_DONE")) {
                    frontendStatus = "success";
                }
            }
            String encodedStatus = "";
            try { encodedStatus = URLEncoder.encode(frontendStatus, StandardCharsets.UTF_8); } catch (Exception ignore) {}
            redirect.append(encodedStatus);
            if (result.containsKey("transactionId")) redirect.append("&transactionId=").append(URLEncoder.encode(result.get("transactionId"), StandardCharsets.UTF_8));
            if (result.containsKey("orderId")) redirect.append("&orderId=").append(URLEncoder.encode(result.get("orderId"), StandardCharsets.UTF_8));
            URI location = URI.create(redirect.toString());
            return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
        } catch (Exception e) {
            api.setCode(9999);
            api.setMessage("Error handling Momo return: " + e.getMessage());
            return ResponseEntity.internalServerError().body(api);
        }
    }
}
