package com.cosmate.service.impl;

import com.cosmate.entity.Transaction;
import com.cosmate.entity.Wallet;
import com.cosmate.repository.TransactionRepository;
import com.cosmate.repository.WalletRepository;
import com.cosmate.service.VnPayService;
import com.cosmate.service.WalletService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VnPayServiceImpl implements VnPayService {

    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final com.cosmate.service.NotificationService notificationService;

    private static final Logger logger = LoggerFactory.getLogger(VnPayServiceImpl.class);

    @Value("${vnp.tmnCode}")
    private String vnpTmnCode;

    @Value("${vnp.hashSecret}")
    private String vnpHashSecret;

    @Value("${vnp.url:http://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpUrlRoot;

    @Override
    @Transactional
    public String createPaymentUrl(Integer userId, BigDecimal amount, String returnUrl) throws Exception {
        // default flow - create its own transaction and use WALLET prefix
        // amount in VND (VNPay expects amount in cents - multiply by 100)
        long amountVnd100 = amount.multiply(new BigDecimal(100)).longValue();

        // ensure wallet exists
        com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(userId).build();
        Wallet wallet = walletService.createForUser(u);

        // create a pending transaction so we can correlate the return
        Transaction pending = Transaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type("CREDIT")
                .paymentMethod("VNPAY")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        pending = transactionRepository.save(pending);
        logger.info("Created pending VnPay transaction id={} userId={} amount={} paymentMethod={}", pending.getId(), userId, amount, pending.getPaymentMethod());

        return createPaymentUrlForTransaction(userId, amount, returnUrl, pending.getId());
    }

    @Override
    @Transactional
    public String createPaymentUrlForTransaction(Integer userId, BigDecimal amount, String returnUrl, Integer transactionId) throws Exception {
        long amountVnd100 = amount.multiply(new BigDecimal(100)).longValue();

        // create txn ref with SUB or WALLET depending on type - here use SUB if transaction type starts with SUBSCRIPTION else WALLET
        String prefix = "WALLET";
        Optional<Transaction> txOpt = transactionRepository.findById(transactionId);
        if (txOpt.isPresent()) {
            Transaction tx = txOpt.get();
            // ensure payment method recorded
            if (tx.getPaymentMethod() == null || tx.getPaymentMethod().isEmpty()) {
                tx.setPaymentMethod("VNPAY");
                transactionRepository.save(tx);
                logger.info("Set paymentMethod=VNPAY for transaction id={}", tx.getId());
            }
            if (tx.getType() != null && tx.getType().toUpperCase().contains("SUBSCRIPTION")) prefix = "SUB";
        }
        String txnRef = prefix + transactionId;

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnpTmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amountVnd100));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", txnRef);
        vnp_Params.put("vnp_OrderInfo", "Payment for txn:" + txnRef);
        vnp_Params.put("vnp_OrderType", "topup");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", returnUrl);
        vnp_Params.put("vnp_CreateDate", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        vnp_Params.put("vnp_ExpireDate", LocalDateTime.now().plusMinutes(15).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        vnp_Params.put("vnp_IpAddr", "127.0.0.1");

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            String value = vnp_Params.get(fieldName);
            if (value != null && value.length() > 0) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(value, StandardCharsets.US_ASCII.toString()));
                query.append(fieldName).append('=').append(URLEncoder.encode(value, StandardCharsets.US_ASCII.toString()));
                if (i < fieldNames.size() - 1) {
                    hashData.append('&');
                    query.append('&');
                }
            }
        }
        String secureHash = hmacSHA512(vnpHashSecret, hashData.toString());
        String payUrl = vnpUrlRoot + "?" + query.toString() + "&vnp_SecureHash=" + secureHash;
        return payUrl;
    }

    @Override
    @Transactional
    public Map<String, String> handleReturn(Map<String, String> params) throws Exception {
        // Validate secure hash
        String vnp_SecureHash = params.get("vnp_SecureHash");
        Map<String, String> copy = new HashMap<>(params);
        copy.remove("vnp_SecureHash");
        copy.remove("vnp_SecureHashType");

        List<String> fieldNames = new ArrayList<>(copy.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        for (int i = 0; i < fieldNames.size(); i++) {
            String k = fieldNames.get(i);
            String v = copy.get(k);
            hashData.append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.US_ASCII.toString()));
            if (i < fieldNames.size() - 1) hashData.append('&');
        }
        String computed = hmacSHA512(vnpHashSecret, hashData.toString());
        Map<String, String> result = new HashMap<>();
        if (!computed.equalsIgnoreCase(vnp_SecureHash)) {
            result.put("status", "INVALID_HASH");
            return result;
        }

        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || !(txnRef.startsWith("WALLET") || txnRef.startsWith("SUB"))) {
            result.put("status", "INVALID_TXNREF");
            return result;
        }

        String idPart;
        if (txnRef.startsWith("WALLET")) idPart = txnRef.substring("WALLET".length());
        else idPart = txnRef.substring("SUB".length());
        Integer txnId;
        try {
            txnId = Integer.valueOf(idPart);
        } catch (NumberFormatException ex) {
            result.put("status", "INVALID_TXNREF");
            return result;
        }

        Optional<Transaction> op = transactionRepository.findById(txnId);
        if (op.isEmpty()) {
            result.put("status", "TRANSACTION_NOT_FOUND");
            return result;
        }

        Transaction t = op.get();

        // ensure payment method recorded
        if (t.getPaymentMethod() == null || t.getPaymentMethod().isEmpty()) {
            t.setPaymentMethod("VNPAY");
            try { transactionRepository.save(t); logger.info("Recorded paymentMethod=VNPAY for transaction id={}", t.getId()); } catch (Exception ex) { logger.error("Failed saving paymentMethod for txn {}", t.getId(), ex); }
        }

        // check amount
        String amountStr = params.get("vnp_Amount");
        long amountFromVnp = Long.parseLong(amountStr);
        BigDecimal amount = BigDecimal.valueOf(amountFromVnp).divide(new BigDecimal(100));
        if (t.getAmount().compareTo(amount) != 0) {
            // mismatch
            t.setStatus("FAILED");
            transactionRepository.save(t);
            result.put("status", "AMOUNT_MISMATCH");
            return result;
        }

        String respCode = params.get("vnp_ResponseCode");

        if (!"00".equals(respCode)) {
            t.setStatus("FAILED");
            transactionRepository.save(t);
            result.put("status", "FAILED");
            return result;
        }

        // idempotency: if already completed, return
        if ("COMPLETED".equalsIgnoreCase(t.getStatus())) {
            result.put("status", "ALREADY_DONE");
            return result;
        }

        // For subscription payments (txnRef starting with SUB) we should NOT add money to wallet;
        // we only mark the transaction as COMPLETED and let subscription flow activate the subscription.
        // detect whether this transaction is an ORDER payment (we shouldn't credit wallet in that case)
        boolean isOrderTransaction = false;
        if (t.getType() != null && t.getType().toUpperCase().startsWith("ORDER#")) isOrderTransaction = true;

        if (!txnRef.startsWith("SUB") && !isOrderTransaction) {
            // credit wallet for non-subscription topups and non-order payments
            Wallet wallet = t.getWallet();
            wallet.setBalance(wallet.getBalance().add(amount));
            walletRepository.save(wallet);
            // Tạo notification cho người dùng khi nạp tiền qua VNPay thành công
            try {
                com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                        .user(wallet.getUser())
                        .type("WALLET_CREDIT")
                        .header("Nạp tiền thành công")
                        .content("Số tiền " + amount + " đã được nạp vào ví của bạn.")
                        .sendAt(LocalDateTime.now())
                        .isRead(false)
                        .build();
                com.cosmate.entity.Notification saved = notificationService.create(n);
                logger.info("Created notification id={} for userId={} txnId={}", saved.getId(), wallet.getUser() == null ? null : wallet.getUser().getId(), t.getId());
            } catch (Exception ex) { logger.error("Error creating notification for wallet credit txnId={}", t.getId(), ex); }
        }

        t.setStatus("COMPLETED");
        t.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(t);

        // If this is a subscription transaction (SUB prefix), subscription activation should be handled by SubscriptionService.finalizeSubscriptionPayment
        // The caller (controller handling VNPay return) can call that service after invoking vnPayService.handleReturn and seeing status OK.

        result.put("status", "OK");
        return result;
    }

    private String hmacSHA512(String key, String data) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        hmac.init(secretKey);
        byte[] raw = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) sb.append(String.format("%02x", b & 0xff));
        return sb.toString().toUpperCase();
    }
}
