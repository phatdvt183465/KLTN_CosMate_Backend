package com.cosmate.repository;

import com.cosmate.entity.User;
import com.cosmate.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Integer> {
    Optional<Wallet> findByUser(User user);
    Optional<Wallet> findByUserId(Integer userId);
}
