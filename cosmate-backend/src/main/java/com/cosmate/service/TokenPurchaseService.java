package com.cosmate.service;

import com.cosmate.dto.response.TokenPurchaseResponse;

import java.util.List;

public interface TokenPurchaseService {
    // Initiate a purchase; returns paymentUrl for offsite providers or null if completed immediately (wallet)
    String initiatePurchase(Integer userId, Integer subscriptionPlanId, String paymentMethod, String returnUrl) throws Exception;

    // Finalize purchase when transaction completes
    void finalizePurchase(Integer transactionId);

    TokenPurchaseResponse getById(Integer id, Integer requesterUserId);
    List<TokenPurchaseResponse> getAll();
    List<TokenPurchaseResponse> getByUser(Integer userId);
}

