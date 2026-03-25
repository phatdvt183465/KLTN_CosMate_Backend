package com.cosmate.repository;

import com.cosmate.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Integer> {
    Optional<Provider> findByUserId(Integer userId);

    @Query(value = "SELECT p.* FROM Providers p " +
            "INNER JOIN Users u ON p.user_id = u.id " +
            "INNER JOIN Roles r ON u.role_id = r.id " +
            "WHERE r.role_name = :roleName",
            nativeQuery = true)
    List<Provider> findProvidersByRoleName(@Param("roleName") String roleName);
}
