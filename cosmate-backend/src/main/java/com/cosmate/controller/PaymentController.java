package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.entity.Order;
import com.cosmate.entity.Transaction;
import com.cosmate.repository.OrderRepository;
import com.cosmate.repository.TransactionRepository;
import com.cosmate.service.MomoService;
import com.cosmate.service.SubscriptionService;
import com.cosmate.service.VnPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;

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
                if (txnRef != null) {
                    if (txnRef.startsWith("SUB")) {
                        String idPart = txnRef.substring("SUB".length());
                        try {
                            Integer txnId = Integer.valueOf(idPart);
                            subscriptionService.finalizeSubscriptionPayment(txnId);
                        } catch (Exception e) {
                            // log and continue; return will still be OK to VNPay
                        }
                    } else if (txnRef.startsWith("WALLET")) {
                        // handle wallet prefix, but transaction may be an ORDER payment (we set tx.type = ORDER#<orderId>)
                        String idPart = txnRef.substring("WALLET".length());
                        try {
                            Integer txnId = Integer.valueOf(idPart);
                            Optional<Transaction> optTx = transactionRepository.findById(txnId);
                            if (optTx.isPresent()) {
                                Transaction tx = optTx.get();
                                String type = tx.getType();
                                if (type != null && type.startsWith("ORDER#")) {
                                    try {
                                        Integer orderId = Integer.valueOf(type.substring("ORDER#".length()));
                                        Optional<Order> oOpt = orderRepository.findById(orderId);
                                        if (oOpt.isPresent()) {
                                            Order o = oOpt.get();
                                            o.setStatus("PAID");
                                            orderRepository.save(o);
                                        }
                                    } catch (Exception ex) {
                                        // ignore
                                    }
                                }
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    } else if (txnRef.startsWith("ORDER#")) {
                        // ORDER#<orderId> mapping: need to find transaction by wallet->id mapping
                        // Instead we can inspect vnp_TxnRef like ORDER#<orderId><txnId>? but simpler: search transaction by id via vnp_TxnRef mapping in Transaction table
                        // VNPay returns vnp_TxnRef which in our createPaymentUrlForTransaction uses prefix+transactionId; so we need to parse transactionId
                        String idPart = null;
                        if (txnRef.startsWith("ORDER#")) idPart = txnRef.substring("ORDER#".length());
                        if (idPart != null) {
                            try {
                                Integer txnId = Integer.valueOf(idPart);
                                Optional<Transaction> optTx = transactionRepository.findById(txnId);
                                if (optTx.isPresent()) {
                                    Transaction tx = optTx.get();
                                    // mark completed
                                    tx.setStatus("COMPLETED");
                                    transactionRepository.save(tx);
                                    // find order id embedded in tx.type (we set type=ORDER#<orderId> earlier). If not found, we can try to parse order id from tx.getType()
                                    String type = tx.getType();
                                    if (type != null && type.startsWith("ORDER#")) {
                                        try {
                                            Integer orderId = Integer.valueOf(type.substring("ORDER#".length()));
                                            Optional<Order> oOpt = orderRepository.findById(orderId);
                                            if (oOpt.isPresent()) {
                                                Order o = oOpt.get();
                                                o.setStatus("PAID");
                                                orderRepository.save(o);
                                            }
                                        } catch (Exception ex) {
                                            // ignore
                                        }
                                    }
                                }
                            } catch (NumberFormatException ignore) {
                            }
                        }
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
            // if OK and transactionId present, finalize actions (subscription/order)
            if ("OK".equals(result.get("status")) && result.containsKey("transactionId")) {
                try {
                    Integer txnId = Integer.valueOf(result.get("transactionId"));
                    Optional<Transaction> optTx = transactionRepository.findById(txnId);
                    if (optTx.isPresent()) {
                        Transaction tx = optTx.get();
                        // mark completed
                        tx.setStatus("COMPLETED");
                        transactionRepository.save(tx);
                        // If tx.type indicates subscription, finalize
                        String type = tx.getType();
                        if (type != null && type.startsWith("SUB")) {
                            try { subscriptionService.finalizeSubscriptionPayment(txnId); } catch (Exception ignore) {}
                        }
                        // If tx.type indicates order, update order status
                        if (type != null && type.startsWith("ORDER#")) {
                            try {
                                Integer orderId = Integer.valueOf(type.substring("ORDER#".length()));
                                Optional<Order> oOpt = orderRepository.findById(orderId);
                                if (oOpt.isPresent()) {
                                    Order o = oOpt.get();
                                    o.setStatus("PAID");
                                    orderRepository.save(o);
                                }
                            } catch (Exception ignore) {}
                        }
                    }
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
            if ("OK".equals(result.get("status")) && result.containsKey("transactionId")) {
                try {
                    Integer txnId = Integer.valueOf(result.get("transactionId"));
                    Optional<Transaction> optTx = transactionRepository.findById(txnId);
                    if (optTx.isPresent()) {
                        Transaction tx = optTx.get();
                        tx.setStatus("COMPLETED");
                        transactionRepository.save(tx);
                        String type = tx.getType();
                        if (type != null && type.startsWith("SUB")) {
                            try { subscriptionService.finalizeSubscriptionPayment(txnId); } catch (Exception ignore) {}
                        }
                        if (type != null && type.startsWith("ORDER#")) {
                            try {
                                Integer orderId = Integer.valueOf(type.substring("ORDER#".length()));
                                Optional<Order> oOpt = orderRepository.findById(orderId);
                                if (oOpt.isPresent()) {
                                    Order o = oOpt.get();
                                    o.setStatus("PAID");
                                    orderRepository.save(o);
                                }
                            } catch (Exception ignore) {}
                        }
                    }
                } catch (Exception ignore) {}
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
