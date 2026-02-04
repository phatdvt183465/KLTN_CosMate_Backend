package com.cosmate.repository;

import com.cosmate.entity.User;
import com.cosmate.entity.WithdrawRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WithdrawRequestRepository extends JpaRepository<WithdrawRequest, Integer> {
    List<WithdrawRequest> findByUser(User user);
    List<WithdrawRequest> findByStatus(String status);
}
