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
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        Transaction t = Transaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type("CREDIT")
                .status("COMPLETED")
                .createdAt(LocalDateTime.now())
                .build();
        return transactionRepository.save(t);
    }

    @Override
    @Transactional
    public Transaction debit(Wallet wallet, BigDecimal amount, String desc, String reference) throws Exception {
        if (wallet.getBalance().compareTo(amount) < 0) throw new Exception("Số dư trong ví không đủ");
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        Transaction t = Transaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type("DEBIT")
                .status("COMPLETED")
                .createdAt(LocalDateTime.now())
                .build();
        return transactionRepository.save(t);
    }

    @Override
    public List<Transaction> getTransactionsForWallet(Wallet wallet) {
        return transactionRepository.findByWalletOrderByCreatedAtDesc(wallet);
    }
}
