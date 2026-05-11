package com.cosmate.service.impl;

import com.cosmate.dto.response.AdminReportSeriesPointResponse;
import com.cosmate.dto.response.ProviderStatisticsResponse;
import com.cosmate.entity.Provider;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.OrderDetailRepository;
import com.cosmate.repository.OrderRepository;
import com.cosmate.repository.ProviderRepository;
import com.cosmate.service.WalletService;
import com.cosmate.service.ProviderStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderStatisticsServiceImpl implements ProviderStatisticsService {

    private final CostumeRepository costumeRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProviderRepository providerRepository;
    private final WalletService walletService;

    @Override
    public ProviderStatisticsResponse getProviderStatistics(Integer providerId, Integer months) {
        // validate provider exists
        Provider p = providerRepository.findById(providerId).orElseThrow(() -> new IllegalArgumentException("Provider not found"));

        long totalCostumes = costumeRepository.countByProviderId(providerId);

        List<com.cosmate.entity.Order> orders = orderRepository.findByProviderIdOrderByCreatedAtDesc(providerId);

        long totalOrders = orders.size();

        long completedOrders = orders.stream().filter(o -> o.getStatus() != null && "COMPLETED".equalsIgnoreCase(o.getStatus())).count();

        Long totalOrderItems = orderDetailRepository.sumNumberOfItemsByProvider(providerId);
        if (totalOrderItems == null) totalOrderItems = 0L;

        // Calculate revenue as totalAmount minus totalDepositAmount (deposit is returned to customer and should not count as revenue)
        BigDecimal totalRevenue = orders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getStatus() != null && "COMPLETED".equalsIgnoreCase(o.getStatus()))
                .map(o -> {
                    BigDecimal total = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
                    BigDecimal deposit = o.getTotalDepositAmount() == null ? BigDecimal.ZERO : o.getTotalDepositAmount();
                    return total.subtract(deposit);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, BigDecimal> byMonth = orders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getStatus() != null && "COMPLETED".equalsIgnoreCase(o.getStatus()))
                .collect(Collectors.groupingBy(o -> o.getCreatedAt().format(monthFmt), Collectors.reducing(BigDecimal.ZERO,
                        o -> {
                            BigDecimal total = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
                            BigDecimal deposit = o.getTotalDepositAmount() == null ? BigDecimal.ZERO : o.getTotalDepositAmount();
                            return total.subtract(deposit);
                        }, BigDecimal::add)));

        // Build fixed-length months series (default to last 12 months) and fill missing months with zero
        int monthsCount = (months != null && months > 0) ? months : 12;
        YearMonth current = YearMonth.now();
        List<YearMonth> monthsList = new ArrayList<>();
        for (int i = monthsCount - 1; i >= 0; i--) {
            monthsList.add(current.minusMonths(i));
        }

        List<AdminReportSeriesPointResponse> revenueByMonth = monthsList.stream()
                .map(ym -> {
                    String label = ym.format(monthFmt);
                    BigDecimal v = byMonth.getOrDefault(label, BigDecimal.ZERO);
                    return AdminReportSeriesPointResponse.builder().label(label).value(v).build();
                })
                .toList();

        // group by quarter label like 2024-Q1
        Map<String, BigDecimal> byQuarter = orders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getStatus() != null && "COMPLETED".equalsIgnoreCase(o.getStatus()))
                .collect(Collectors.groupingBy(o -> {
                    int year = o.getCreatedAt().getYear();
                    int month = o.getCreatedAt().getMonthValue();
                    int q = (month - 1) / 3 + 1;
                    return String.format("%d-Q%d", year, q);
                }, Collectors.reducing(BigDecimal.ZERO, o -> {
                    BigDecimal total = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
                    BigDecimal deposit = o.getTotalDepositAmount() == null ? BigDecimal.ZERO : o.getTotalDepositAmount();
                    return total.subtract(deposit);
                }, BigDecimal::add)));

        List<AdminReportSeriesPointResponse> revenueByQuarter = byQuarter.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> AdminReportSeriesPointResponse.builder().label(e.getKey()).value(e.getValue()).build())
                .toList();

        return ProviderStatisticsResponse.builder()
                .totalCostumes(totalCostumes)
                .totalOrders(totalOrders)
                .totalOrderItems(totalOrderItems)
                .completedOrders(completedOrders)
                .totalRevenue(totalRevenue)
                .revenueByMonth(revenueByMonth)
                .revenueByQuarter(revenueByQuarter)
                .build();
    }

    @Override
    public java.util.List<com.cosmate.dto.response.OrderStatusCountResponse> getOrderCountsByStatus(Integer providerId) {
        // validate provider exists
        Provider p = providerRepository.findById(providerId).orElseThrow(() -> new IllegalArgumentException("Provider not found"));
        List<com.cosmate.entity.Order> orders = orderRepository.findByProviderIdOrderByCreatedAtDesc(providerId);
        Map<String, Long> grouped = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getStatus() == null ? "UNKNOWN" : o.getStatus(), Collectors.counting()));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> com.cosmate.dto.response.OrderStatusCountResponse.builder().status(e.getKey()).count(e.getValue()).build())
                .toList();
    }

    @Override
    public java.util.List<com.cosmate.dto.response.TransactionResponse> getRecentWalletTransactions(Integer providerId, Integer limit) {
        Provider p = providerRepository.findById(providerId).orElseThrow(() -> new IllegalArgumentException("Provider not found"));
        var opt = walletService.getByUserId(p.getUserId());
        if (opt.isEmpty()) return java.util.List.of();
        var wallet = opt.get();
        List<com.cosmate.entity.Transaction> txs = walletService.getTransactionsForWallet(wallet);
        int lim = (limit != null && limit > 0) ? limit : 10;
        return txs.stream().map(t -> com.cosmate.dto.response.TransactionResponse.builder()
                .id(t.getId())
                .amount(t.getAmount())
                .type(t.getType())
                .status(t.getStatus())
                .paymentMethod(t.getPaymentMethod())
                .walletId(t.getWallet() == null ? null : t.getWallet().getWalletId())
                .orderId(t.getOrder() == null ? null : t.getOrder().getId())
                .createdAt(t.getCreatedAt())
                .build())
                .limit(lim)
                .toList();
    }
}

