package com.cosmate.service;

import com.cosmate.entity.Transaction;
import com.cosmate.entity.User;
import com.cosmate.entity.Wallet;
import com.cosmate.entity.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface WalletService {
    Wallet createForUser(User user);
    Optional<Wallet> getByUser(User user);
    Optional<Wallet> getByUserId(Integer userId);
    Transaction credit(Wallet wallet, BigDecimal amount, String desc, String reference);
    Transaction debit(Wallet wallet, BigDecimal amount, String desc, String reference) throws Exception;
    List<Transaction> getTransactionsForWallet(Wallet wallet);

    // New overloads with optional payment metadata
    Transaction credit(Wallet wallet, BigDecimal amount, String desc, String reference, String paymentMethod, Order order);
    Transaction debit(Wallet wallet, BigDecimal amount, String desc, String reference, String paymentMethod, Order order) throws Exception;
}
