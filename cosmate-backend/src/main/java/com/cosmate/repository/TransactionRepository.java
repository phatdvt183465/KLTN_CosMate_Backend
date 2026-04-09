package com.cosmate.repository;

import com.cosmate.entity.Transaction;
import com.cosmate.entity.Wallet;
import com.cosmate.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    List<Transaction> findByWalletOrderByCreatedAtDesc(Wallet wallet);
    // Use nested property name matching Wallet.walletId field
    List<Transaction> findByWallet_WalletIdOrderByCreatedAtDesc(Integer walletId);

    // New: find transactions by Order
    List<Transaction> findByOrderOrderByCreatedAtDesc(Order order);
    List<Transaction> findByOrder_IdOrderByCreatedAtDesc(Integer orderId);
    // find transactions by status
    List<Transaction> findByStatus(String status);
}
