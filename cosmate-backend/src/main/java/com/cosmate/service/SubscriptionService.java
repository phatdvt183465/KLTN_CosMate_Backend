package com.cosmate.service;

import com.cosmate.entity.SubscriptionPlan;
import com.cosmate.entity.ProviderSubscription;

import java.util.List;

public interface SubscriptionService {
    SubscriptionPlan createPlan(SubscriptionPlan plan);
    SubscriptionPlan updatePlan(Integer id, SubscriptionPlan plan);
    List<SubscriptionPlan> listPlans();

    // Provider actions
    String initiateProviderSubscription(Integer providerUserId, Integer planId, String returnUrl) throws Exception;
    ProviderSubscription finalizeSubscriptionPayment(Integer transactionId) throws Exception; // called when VnPay return handled

    // New: query subscriptions
    List<ProviderSubscription> listByProviderUserId(Integer providerUserId);
    List<ProviderSubscription> listAllSubscriptions();
}
