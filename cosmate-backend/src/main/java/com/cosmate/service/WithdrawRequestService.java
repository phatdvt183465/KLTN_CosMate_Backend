package com.cosmate.service;

import com.cosmate.dto.request.WithdrawRequestCreate;
import com.cosmate.entity.User;
import com.cosmate.entity.WithdrawRequest;

import java.util.List;

public interface WithdrawRequestService {
    WithdrawRequest createWithdrawRequest(User user, WithdrawRequestCreate req) throws Exception;
    List<WithdrawRequest> getRequestsForUser(User user);
    List<WithdrawRequest> getAllRequests();
    WithdrawRequest approveRequest(Integer requestId, User actionBy) throws Exception;
    WithdrawRequest rejectRequest(Integer requestId, User actionBy, String reason) throws Exception;
}
