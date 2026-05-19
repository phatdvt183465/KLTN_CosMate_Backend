package com.cosmate.repository;

import com.cosmate.entity.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {
    Optional<Token> findByToken(String token);

    @Modifying
    @Transactional
    @Query(value = "UPDATE Tokens SET user_id = :userId, used = 1 WHERE token = :token AND used = 0 AND expires_at > GETDATE()", nativeQuery = true)
    int approveToken(@Param("userId") Long userId, @Param("token") String token);
}

