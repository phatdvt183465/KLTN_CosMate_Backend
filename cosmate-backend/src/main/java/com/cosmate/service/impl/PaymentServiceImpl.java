package com.cosmate.service.impl;

import com.cosmate.entity.Order;
import com.cosmate.entity.Transaction;
import com.cosmate.repository.OrderRepository;
import com.cosmate.repository.TransactionRepository;
import com.cosmate.service.MomoService;
import com.cosmate.service.SubscriptionService;
import com.cosmate.service.VnPayService;
import com.cosmate.service.PaymentService;
import com.cosmate.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final VnPayService vnPayService;
    private final MomoService momoService;
    private final SubscriptionService subscriptionService;
    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final com.cosmate.service.NotificationService notificationService;

    @Override
    public Map<String,String> processVnPayReturn(Map<String,String> allParams) throws Exception {
        Map<String,String> result = vnPayService.handleReturn(allParams);
        String status = result.get("status");
        if ("OK".equals(status) || "ALREADY_DONE".equals(status)) {
            String txnRef = allParams.get("vnp_TxnRef");
            Integer forwardedTxnId = null;
            Integer forwardedOrderId = null;
            if (result.containsKey("transactionId")) {
                try { forwardedTxnId = Integer.valueOf(result.get("transactionId")); } catch (Exception ignored) {}
            }
            if (result.containsKey("orderId")) {
                try { forwardedOrderId = Integer.valueOf(result.get("orderId")); } catch (Exception ignored) {}
            }
            if ("OK".equals(status) && txnRef != null) {
                if (txnRef.startsWith("SUB")) {
                    try { Integer txnId = Integer.valueOf(txnRef.substring(3)); subscriptionService.finalizeSubscriptionPayment(txnId); forwardedTxnId = txnId; } catch (Exception ignored) {}
                } else if (txnRef.startsWith("WALLET")) {
                    try { Integer txnId = Integer.valueOf(txnRef.substring(6)); forwardedTxnId = txnId; Optional<Transaction> optTx = transactionRepository.findById(txnId); if (optTx.isPresent()) { Transaction tx = optTx.get(); String type = tx.getType(); if (type != null && type.startsWith("ORDER#")) { Integer orderId = Integer.valueOf(type.substring("ORDER#".length())); forwardedOrderId = orderId; Optional<Order> oOpt = orderRepository.findById(orderId); if (oOpt.isPresent()) { Order o = oOpt.get(); o.setStatus("PAID"); orderRepository.save(o); } } } } catch (Exception ignored) {}
                } else if (txnRef.startsWith("ORDER#")) {
                    try { Integer txnId = Integer.valueOf(txnRef.substring("ORDER#".length())); forwardedTxnId = txnId; Optional<Transaction> optTx = transactionRepository.findById(txnId); if (optTx.isPresent()) { Transaction tx = optTx.get(); tx.setStatus("COMPLETED"); transactionRepository.save(tx); String type = tx.getType(); if (type != null && type.startsWith("ORDER#")) { Integer orderId = Integer.valueOf(type.substring("ORDER#".length())); forwardedOrderId = orderId; Optional<Order> oOpt = orderRepository.findById(orderId); if (oOpt.isPresent()) { Order o = oOpt.get(); o.setStatus("PAID"); orderRepository.save(o); } } } } catch (Exception ignored) {}
                }
            }
            Map<String,String> res = new HashMap<>();
            res.putAll(result);
            if (forwardedTxnId != null) res.put("transactionId", forwardedTxnId.toString());
            if (forwardedOrderId != null) res.put("orderId", forwardedOrderId.toString());
            return res;
        }
        return result;
    }

    @Override
    public Map<String,String> processMomoNotification(Map<String,String> allParams) throws Exception {
        Map<String,String> result = momoService.handleNotification(allParams);
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
                        try { subscriptionService.finalizeSubscriptionPayment(txnId); } catch (Exception ignored) {}
                    }
                    if (type != null && type.startsWith("ORDER#")) {
                        try { Integer orderId = Integer.valueOf(type.substring("ORDER#".length())); Optional<Order> oOpt = orderRepository.findById(orderId); if (oOpt.isPresent()) { Order o = oOpt.get(); o.setStatus("PAID"); orderRepository.save(o); } } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    @Override
    public Map<String,String> processMomoReturn(Map<String,String> allParams) throws Exception {
        // reuse notification handling then build response map like controller expects
        return processMomoNotification(allParams);
    }
}

