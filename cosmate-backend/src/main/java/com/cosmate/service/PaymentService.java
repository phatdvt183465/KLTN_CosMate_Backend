package com.cosmate.service;

import java.util.Map;

public interface PaymentService {
    Map<String,String> processVnPayReturn(Map<String,String> allParams) throws Exception;
    Map<String,String> processMomoNotification(Map<String,String> allParams) throws Exception;
    Map<String,String> processMomoReturn(Map<String,String> allParams) throws Exception;
}

