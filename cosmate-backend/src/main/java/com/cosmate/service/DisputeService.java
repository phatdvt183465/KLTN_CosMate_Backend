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
    // whether a given userId may view the dispute (opener or provider owner)
    boolean canViewDispute(Integer disputeId, Integer userId);
    // debug helper that aggregates dispute/order/wallet/transactions info for staff
    java.util.Map<String,Object> debugDispute(Integer disputeId) throws Exception;
}
