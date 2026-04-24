package com.cosmate.repository;

import com.cosmate.entity.TokenPurchaseHistory;
import com.cosmate.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TokenPurchaseHistoryRepository extends JpaRepository<TokenPurchaseHistory, Integer> {
    List<TokenPurchaseHistory> findByUser(User user);
    List<TokenPurchaseHistory> findByStatusAndPurchaseDateBefore(String status, LocalDateTime before);
}

