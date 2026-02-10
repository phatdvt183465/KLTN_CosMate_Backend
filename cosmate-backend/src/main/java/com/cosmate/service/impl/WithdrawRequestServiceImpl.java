package com.cosmate.service.impl;

import com.cosmate.dto.request.WithdrawRequestCreate;
import com.cosmate.entity.Provider;
import com.cosmate.entity.Transaction;
import com.cosmate.entity.User;
import com.cosmate.entity.Wallet;
import com.cosmate.entity.WithdrawRequest;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.repository.TransactionRepository;
import com.cosmate.repository.WithdrawRequestRepository;
import com.cosmate.service.WalletService;
import com.cosmate.service.WithdrawRequestService;
import com.cosmate.util.RoleUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WithdrawRequestServiceImpl implements WithdrawRequestService {

    private final WithdrawRequestRepository withdrawRequestRepository;
    private final WalletService walletService;
    private final ProviderRepository providerRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public WithdrawRequest createWithdrawRequest(User user, WithdrawRequestCreate req) throws Exception {
        // Ensure wallet exists
        Wallet wallet = walletService.getByUser(user).orElseThrow(() -> new Exception("Không tìm thấy ví"));

        // amount > 0 and <= balance
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) throw new Exception("Số tiền không hợp lệ");
        if (wallet.getBalance().compareTo(req.getAmount()) < 0) throw new Exception("Số dư trong ví không đủ");

        String bankAccount = req.getBankAccountNumber();
        String bankName = req.getBankName();

        // if provider and bank details missing, try provider default
        if (user.getRoles() != null && user.getRoles().stream().anyMatch(RoleUtils::isProviderRole)) {
            if ((bankAccount == null || bankAccount.isBlank()) || (bankName == null || bankName.isBlank())) {
                Optional<Provider> provOpt = providerRepository.findByUserId(user.getId());
                if (provOpt.isPresent()) {
                    Provider p = provOpt.get();
                    if (bankAccount == null || bankAccount.isBlank()) bankAccount = p.getBankAccountNumber();
                    if (bankName == null || bankName.isBlank()) bankName = p.getBankName();
                }
            }
        }

        if (bankAccount == null || bankAccount.isBlank() || bankName == null || bankName.isBlank()) {
            throw new Exception("Thiếu thông tin tài khoản ngân hàng");
        }

        WithdrawRequest wr = WithdrawRequest.builder()
                .user(user)
                .wallet(wallet)
                .amount(req.getAmount())
                .bankAccountNumber(bankAccount)
                .bankName(bankName)
                .status("PENDING")
                .build();

        return withdrawRequestRepository.save(wr);
    }

    @Override
    public List<WithdrawRequest> getRequestsForUser(User user) {
        return withdrawRequestRepository.findByUser(user);
    }

    @Override
    public List<WithdrawRequest> getAllRequests() {
        return withdrawRequestRepository.findAll();
    }

    @Override
    @Transactional
    public WithdrawRequest approveRequest(Integer requestId, User actionBy) throws Exception {
        WithdrawRequest wr = withdrawRequestRepository.findById(requestId).orElseThrow(() -> new Exception("Yêu cầu rút tiền không tồn tại"));
        if (!"PENDING".equalsIgnoreCase(wr.getStatus())) throw new Exception("Yêu cầu đã được xử lý");

        Wallet wallet = wr.getWallet();
        // Check funds again
        if (wallet.getBalance().compareTo(wr.getAmount()) < 0) throw new Exception("Số dư trong ví không đủ");

        // Debit wallet and create transaction
        Transaction t = walletService.debit(wallet, wr.getAmount(), "Rút tiền - duyệt", "WITHDRAW:" + wr.getId());

        wr.setStatus("APPROVED");
        withdrawRequestRepository.save(wr);

        return wr;
    }

    @Override
    @Transactional
    public WithdrawRequest rejectRequest(Integer requestId, User actionBy, String reason) throws Exception {
        WithdrawRequest wr = withdrawRequestRepository.findById(requestId).orElseThrow(() -> new Exception("Yêu cầu rút tiền không tồn tại"));
        if (!"PENDING".equalsIgnoreCase(wr.getStatus())) throw new Exception("Yêu cầu đã được xử lý");

        wr.setStatus("REJECTED");
        withdrawRequestRepository.save(wr);
        return wr;
    }
}
