package com.cosmate.service;

import com.cosmate.entity.Wallet;

import java.math.BigDecimal;
import java.util.Map;

public interface VnPayService {
    String createPaymentUrl(Integer userId, BigDecimal amount, String returnUrl) throws Exception;
    Map<String, String> handleReturn(Map<String, String> params) throws Exception;
    String createPaymentUrlForTransaction(Integer userId, BigDecimal amount, String returnUrl, Integer transactionId) throws Exception;
}
