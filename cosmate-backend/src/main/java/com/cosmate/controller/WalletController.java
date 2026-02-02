package com.cosmate.controller;

import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.TransactionResponse;
import com.cosmate.dto.response.WalletResponse;
import com.cosmate.entity.Transaction;
import com.cosmate.entity.Wallet;
import com.cosmate.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private static final Logger log = LoggerFactory.getLogger(WalletController.class);

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
            log.debug("Unable to parse current principal to userId: {}", principal);
            return null;
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet() {
        Integer userId = getCurrentUserId();
        ApiResponse<WalletResponse> api = new ApiResponse<>();
        if (userId == null) {
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }

        Wallet wallet = walletService.getByUserId(userId).orElse(null);
        if (wallet == null) {
            api.setCode(1004);
            api.setMessage("Wallet not found");
            return ResponseEntity.ok(api);
        }

        WalletResponse resp = WalletResponse.builder()
                .walletId(wallet.getWalletId())
                .userId(wallet.getUser().getId())
                .balance(wallet.getBalance())
                .depositBalance(wallet.getDepositBalance())
                .build();

        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }

    @GetMapping("/me/transactions")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getMyTransactions() {
        Integer userId = getCurrentUserId();
        ApiResponse<List<TransactionResponse>> api = new ApiResponse<>();
        if (userId == null) {
            api.setCode(1001);
            api.setMessage("Chưa xác thực - Vui lòng đăng nhập");
            return ResponseEntity.status(401).body(api);
        }

        Wallet wallet = walletService.getByUserId(userId).orElse(null);
        if (wallet == null) {
            api.setCode(1004);
            api.setMessage("Wallet not found");
            return ResponseEntity.ok(api);
        }

        List<Transaction> txs = walletService.getTransactionsForWallet(wallet);
        List<TransactionResponse> resp = txs.stream().map(t -> TransactionResponse.builder()
                .id(t.getId())
                .amount(t.getAmount())
                .type(t.getType())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .build()).collect(Collectors.toList());

        api.setCode(0);
        api.setMessage("OK");
        api.setResult(resp);
        return ResponseEntity.ok(api);
    }
}
