package com.cosmate.service.impl;

import com.cosmate.entity.Transaction;
import com.cosmate.entity.Wallet;
import com.cosmate.repository.TransactionRepository;
import com.cosmate.repository.WalletRepository;
import com.cosmate.service.MomoService;
import com.cosmate.service.WalletService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MomoServiceImpl implements MomoService {

    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final com.cosmate.service.NotificationService notificationService;

    private static final Logger logger = LoggerFactory.getLogger(MomoServiceImpl.class);

    @Value("${momo.endpoint:https://test-payment.momo.vn/v2/gateway/api}")
    private String momoEndpoint;

    @Value("${momo.accessKey}")
    private String accessKey;

    @Value("${momo.partnerCode}")
    private String partnerCode;

    @Value("${momo.secretKey}")
    private String secretKey;

    @Value("${momo.returnUrl:http://localhost:8080/api/payments/momo-return}")
    private String defaultReturnUrl;

    @Override
    @Transactional
    public String createPaymentUrl(Integer userId, BigDecimal amount, String returnUrl) throws Exception {
        // create pending transaction similar to VnPay implementation
        com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(userId).build();
        Wallet wallet = walletService.createForUser(u);

        Transaction pending = Transaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type("CREDIT")
                .paymentMethod("MOMO")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        pending = transactionRepository.save(pending);
        logger.info("Created pending Momo transaction id={} userId={} amount={} paymentMethod={}", pending.getId(), userId, amount, pending.getPaymentMethod());
        return createPaymentUrlForTransaction(userId, amount, returnUrl, pending.getId());
    }

    @Override
    @Transactional
    public String createPaymentUrlForTransaction(Integer userId, BigDecimal amount, String returnUrl, Integer transactionId) throws Exception {
        if (returnUrl == null || returnUrl.isEmpty()) returnUrl = defaultReturnUrl;

        Optional<Transaction> txOpt = transactionRepository.findById(transactionId);
        String prefix = "WALLET";
        if (txOpt.isPresent()) {
            Transaction tx = txOpt.get();
            // ensure payment method is recorded for this txn
            if (tx.getPaymentMethod() == null || tx.getPaymentMethod().isEmpty()) {
                tx.setPaymentMethod("MOMO");
                transactionRepository.save(tx);
                logger.info("Set paymentMethod=MOMO for transaction id={}", tx.getId());
            }
            if (tx.getType() != null && tx.getType().toUpperCase().contains("SUBSCRIPTION")) prefix = "SUB";
        }
        String orderId = prefix + transactionId;

        // amount expected by Momo API is in smallest currency unit (VND) - amount*1000? For Momo it's usually VND without extra zeros; keep it simple: use amount.longValue()
        long amountVnd = amount.longValue();

        String requestId = partnerCode + System.currentTimeMillis();
        String orderInfo = "Payment for " + orderId;
        String redirectUrl = returnUrl;
        String ipnUrl = returnUrl; // callback

        String rawSignature = "accessKey=" + accessKey +
                "&amount=" + amountVnd +
                "&extraData=" + "" +
                "&ipnUrl=" + ipnUrl +
                "&orderId=" + orderId +
                "&orderInfo=" + orderInfo +
                "&partnerCode=" + partnerCode +
                "&redirectUrl=" + redirectUrl +
                "&requestId=" + requestId +
                "&requestType=" + "captureWallet";

        String signature = hmacSHA256(secretKey, rawSignature);

        Map<String, Object> payload = new HashMap<>();
        payload.put("partnerCode", partnerCode);
        payload.put("accessKey", accessKey);
        payload.put("requestId", requestId);
        payload.put("amount", String.valueOf(amountVnd));
        payload.put("orderId", orderId);
        payload.put("orderInfo", orderInfo);
        payload.put("redirectUrl", redirectUrl);
        payload.put("ipnUrl", ipnUrl);
        payload.put("lang", "en");
        payload.put("extraData", "");
        payload.put("requestType", "captureWallet");
        payload.put("signature", signature);

        String endpoint = momoEndpoint + "/create";
        String json = toJson(payload);

        URL url = new URL(endpoint);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        con.setDoOutput(true);
        try (OutputStream os = con.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int status = con.getResponseCode();
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        con.disconnect();

        // parse response to extract payUrl or checkoutUrl / deeplink
        Map<String, Object> resp = parseJson(content.toString());
        if (resp.containsKey("payUrl")) return resp.get("payUrl").toString();
        if (resp.containsKey("checkoutUrl")) return resp.get("checkoutUrl").toString();
        // fallback: return stringified response
        return content.toString();
    }

    @Override
    public Map<String, String> handleNotification(Map<String, String> params) throws Exception {
        // Momo IPN handling: verify signature and update transaction
        String signature = params.get("signature");
        // Build raw data according to Momo docs - use canonical field order commonly used by Momo
        String extraData = params.getOrDefault("extraData", "");
        String message = params.getOrDefault("message", "");
        String orderId = params.getOrDefault("orderId", "");
        String orderInfo = params.getOrDefault("orderInfo", "");
        String orderType = params.getOrDefault("orderType", "");
        String transId = params.getOrDefault("transId", "");
        String responseTime = params.getOrDefault("responseTime", "");
        String resultCode = params.getOrDefault("resultCode", "");
        String requestId = params.getOrDefault("requestId", "");
        String payType = params.getOrDefault("payType", "");
        String partner = params.getOrDefault("partnerCode", partnerCode);
        String amountStr = params.getOrDefault("amount", "");

        String raw = "accessKey=" + accessKey +
                "&amount=" + amountStr +
                "&extraData=" + extraData +
                "&message=" + message +
                "&orderId=" + orderId +
                "&orderInfo=" + orderInfo +
                "&orderType=" + orderType +
                "&partnerCode=" + partner +
                "&payType=" + payType +
                "&requestId=" + requestId +
                "&responseTime=" + responseTime +
                "&resultCode=" + resultCode +
                "&transId=" + transId;

        String computed = hmacSHA256(secretKey, raw);
        Map<String, String> result = new HashMap<>();
        if (signature == null || !computed.equalsIgnoreCase(signature)) {
            // If provider indicates a failure/cancel (resultCode != 0), treat as user cancellation instead of signature error
            String rc = params.getOrDefault("resultCode", "");
            if (rc != null && !rc.isEmpty() && !"0".equals(rc)) {
                // attempt to mark transaction cancelled if orderId present
                if (orderId != null && !orderId.isEmpty()) {
                    try {
                        String idPart2;
                        if (orderId.startsWith("WALLET")) idPart2 = orderId.substring("WALLET".length());
                        else if (orderId.startsWith("SUB")) idPart2 = orderId.substring("SUB".length());
                        else idPart2 = null;
                        if (idPart2 != null) {
                            Integer tid = Integer.valueOf(idPart2);
                            Optional<Transaction> opt = transactionRepository.findById(tid);
                            if (opt.isPresent()) {
                                Transaction tx = opt.get();
                                tx.setStatus("CANCELLED");
                                transactionRepository.save(tx);
                                result.put("status", "CANCELLED");
                                result.put("transactionId", String.valueOf(tid));
                                result.put("computed", computed);
                                result.put("provided", signature == null ? "" : signature);
                                return result;
                            }
                        }
                    } catch (Exception ignore) {
                    }
                }
                result.put("status", "CANCELLED");
                result.put("computed", computed);
                result.put("provided", signature == null ? "" : signature);
                return result;
            }

            result.put("status", "INVALID_SIGNATURE");
            // include computed/provided for debugging in non-production (optional)
            result.put("computed", computed);
            result.put("provided", signature == null ? "" : signature);
            return result;
        }

        if (orderId == null || orderId.isEmpty()) {
            result.put("status", "INVALID_ORDERID");
            return result;
        }

        String idPart;
        if (orderId.startsWith("WALLET")) idPart = orderId.substring("WALLET".length());
        else if (orderId.startsWith("SUB")) idPart = orderId.substring("SUB".length());
        else {
            result.put("status", "INVALID_TXNREF");
            return result;
        }

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
        // idempotency: if this transaction is already completed, do not credit wallet again
        if (t.getStatus() != null && t.getStatus().equalsIgnoreCase("COMPLETED")) {
            result.put("status", "ALREADY_DONE");
            result.put("transactionId", String.valueOf(txnId));
            return result;
        }
        // ensure payment method recorded
        if (t.getPaymentMethod() == null || t.getPaymentMethod().isEmpty()) {
            t.setPaymentMethod("MOMO");
            try {
                transactionRepository.save(t);
                logger.info("Recorded paymentMethod=MOMO for transaction id={}", t.getId());
            } catch (Exception ex) {
                logger.error("Failed to save paymentMethod for transaction id={}", t.getId(), ex);
            }
        }
        long amountFromMomo = 0L;
        try {
            amountFromMomo = Long.parseLong(amountStr);
        } catch (NumberFormatException ignore) {
        }
        BigDecimal amount = BigDecimal.valueOf(amountFromMomo);

        // Momo returns amount in VND; compare directly
        if (t.getAmount().compareTo(amount) != 0) {
            t.setStatus("FAILED");
            transactionRepository.save(t);
            result.put("status", "AMOUNT_MISMATCH");
            return result;
        }

        if (!"0".equals(resultCode)) {
            t.setStatus("FAILED");
            transactionRepository.save(t);
            result.put("status", "FAILED");
            return result;
        }

        // mark completed and update wallet if not subscription
        if (!orderId.startsWith("SUB")) {
            Wallet wallet = t.getWallet();
            wallet.setBalance(wallet.getBalance().add(amount));
            walletRepository.save(wallet);
            // Tạo notification cho người dùng khi nạp tiền qua Momo thành công
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
            } catch (Exception ex) {
                logger.error("Error creating notification for wallet credit txnId={}", t.getId(), ex);
            }
        }

        t.setStatus("COMPLETED");
        t.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(t);

        result.put("status", "OK");
        result.put("transactionId", String.valueOf(txnId));
        return result;
    }

    private String hmacSHA256(String key, String data) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] bytes = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder();
        for (byte b : bytes) {
            hash.append(String.format("%02x", b & 0xff));
        }
        return hash.toString();
    }

    // Simple JSON utilities (avoid adding dependencies)
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append('"').append(':');
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number) sb.append(v.toString());
            else sb.append('"').append(escape(v.toString())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private Map<String, Object> parseJson(String s) {
        // Very naive parser for small responses - for robust code use Jackson/Gson. Here we do a simple search for keys payUrl/checkoutUrl
        Map<String, Object> out = new HashMap<>();
        if (s.contains("payUrl")) {
            int i = s.indexOf("payUrl");
            int colon = s.indexOf(':', i);
            int q1 = s.indexOf('"', colon);
            int q2 = s.indexOf('"', q1 + 1);
            if (q1 > 0 && q2 > q1) out.put("payUrl", s.substring(q1 + 1, q2));
        }
        if (s.contains("checkoutUrl")) {
            int i = s.indexOf("checkoutUrl");
            int colon = s.indexOf(':', i);
            int q1 = s.indexOf('"', colon);
            int q2 = s.indexOf('"', q1 + 1);
            if (q1 > 0 && q2 > q1) out.put("checkoutUrl", s.substring(q1 + 1, q2));
        }
        return out;
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
