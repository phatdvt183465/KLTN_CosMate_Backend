package com.cosmate.service;

import java.math.BigDecimal;
import java.util.Map;

public interface MomoService {
    String createPaymentUrl(Integer userId, BigDecimal amount, String returnUrl) throws Exception;
    String createPaymentUrlForTransaction(Integer userId, BigDecimal amount, String returnUrl, Integer transactionId) throws Exception;
    Map<String, String> handleNotification(Map<String, String> params) throws Exception;
}
