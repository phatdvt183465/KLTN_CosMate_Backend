package com.cosmate.repository;

import com.cosmate.base.crud.BaseCrudRepository;
import com.cosmate.entity.User;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends BaseCrudRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    List<User> findByFullNameContainingIgnoreCaseAndStatus(String fullName, String status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.numberOfToken = u.numberOfToken - :amount WHERE u.id = :id AND u.numberOfToken >= :amount")
    int deductTokensSafe(@org.springframework.data.repository.query.Param("id") Integer id, @org.springframework.data.repository.query.Param("amount") int amount);
}