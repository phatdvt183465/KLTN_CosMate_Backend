package com.cosmate.service.impl;

import com.cosmate.entity.Transaction;
import com.cosmate.entity.User;
import com.cosmate.entity.Wallet;
import com.cosmate.repository.TransactionRepository;
import com.cosmate.repository.WalletRepository;
import com.cosmate.service.WalletService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final com.cosmate.service.NotificationService notificationService;

    @Override
    @Transactional
    public Wallet createForUser(User user) {
        // If wallet already exists, return it
        Optional<Wallet> existing = walletRepository.findByUser(user);
        if (existing.isPresent()) return existing.get();

        Wallet w = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .depositBalance(BigDecimal.ZERO)
                .build();
        return walletRepository.save(w);
    }

    @Override
    public Optional<Wallet> getByUser(User user) {
        return walletRepository.findByUser(user);
    }

    @Override
    public Optional<Wallet> getByUserId(Integer userId) {
        return walletRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public Transaction credit(Wallet wallet, BigDecimal amount, String desc, String reference) {
        return credit(wallet, amount, desc, reference, null, null);
    }

    @Override
    @Transactional
    public Transaction debit(Wallet wallet, BigDecimal amount, String desc, String reference) throws Exception {
        return debit(wallet, amount, desc, reference, null, null);
    }

    @Override
    public List<Transaction> getTransactionsForWallet(Wallet wallet) {
        return transactionRepository.findByWalletOrderByCreatedAtDesc(wallet);
    }

    @Override
    @Transactional
    public Transaction credit(Wallet wallet, BigDecimal amount, String desc, String reference, String paymentMethod, com.cosmate.entity.Order order) {
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        // derive txType from reference or fallback
        String txType = "CREDIT";
        try {
            if (reference != null && reference.contains(":")) txType = reference.split(":", 2)[0];
        } catch (Exception ignored) {}

        // store the canonical txType token into Transaction.type (keep English token for programmatic checks)
        String storedType = txType;

        Transaction t = Transaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type(storedType)
                .paymentMethod(paymentMethod)
                .order(order)
                .status("COMPLETED")
                .createdAt(LocalDateTime.now())
                .build();
        Transaction saved = transactionRepository.save(t);

        // Tạo notification cho người dùng khi nạp tiền
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(wallet.getUser())
                    .type("WALLET_CREDIT")
                    .header("Nạp tiền thành công")
                    .content("Số tiền " + amount + " đã được nạp vào ví của bạn.")
                    .sendAt(LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}
        return saved;
    }

    @Override
    @Transactional
    public Transaction debit(Wallet wallet, BigDecimal amount, String desc, String reference, String paymentMethod, com.cosmate.entity.Order order) throws Exception {
        if (wallet.getBalance().compareTo(amount) < 0) throw new Exception("Số dư trong ví không đủ");
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        String txType = "DEBIT";
        try {
            if (reference != null && reference.contains(":")) txType = reference.split(":", 2)[0];
        } catch (Exception ignored) {}

        String storedType = txType;

        Transaction t = Transaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type(storedType)
                .paymentMethod(paymentMethod)
                .order(order)
                .status("COMPLETED")
                .createdAt(LocalDateTime.now())
                .build();
        Transaction saved = transactionRepository.save(t);
        // Tạo notification cho người dùng khi rút tiền hoặc trừ tiền
        try {
            // Decide header based on token (detect withdraw by token name)
            String hdr = (storedType != null && storedType.toUpperCase().contains("WITHDRAW")) ? "Rút tiền thành công" : "Giao dịch ví";
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(wallet.getUser())
                    .type("WALLET_DEBIT")
                    .header(hdr)
                    .content("Số tiền " + amount + " đã được trừ khỏi ví của bạn.")
                    .sendAt(LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}
        return saved;
    }
    
}
