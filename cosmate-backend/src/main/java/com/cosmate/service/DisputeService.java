package com.cosmate.service;

import com.cosmate.entity.Dispute;
import com.cosmate.entity.DisputeResult;

import java.util.List;
import java.util.Optional;

public interface DisputeService {
    Dispute createDispute(Integer openerUserId, Integer orderId, String reason) throws Exception;
    Optional<Dispute> getById(Integer id);
    List<Dispute> listByOrder(Integer orderId);
    List<Dispute> listByUser(Integer userId);
    List<Dispute> listByStatus(String status);
    DisputeResult resolveDispute(Integer resolverUserId, Integer disputeId, DisputeResult result) throws Exception;
}
