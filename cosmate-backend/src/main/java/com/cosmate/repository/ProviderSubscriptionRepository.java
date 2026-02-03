package com.cosmate.repository;

import com.cosmate.entity.ProviderSubscription;
import com.cosmate.entity.Provider;
import com.cosmate.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderSubscriptionRepository extends JpaRepository<ProviderSubscription, Integer> {
    List<ProviderSubscription> findByProviderOrderByStartDateDesc(Provider provider);
    Optional<ProviderSubscription> findFirstByProviderOrderByEndDateDesc(Provider provider);
    Optional<ProviderSubscription> findByTransaction(Transaction tx);
}
