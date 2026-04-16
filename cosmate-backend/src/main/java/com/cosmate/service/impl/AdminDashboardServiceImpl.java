package com.cosmate.service.impl;

import com.cosmate.dto.response.AdminDashboardSummaryResponse;
import com.cosmate.dto.response.AdminReportSeriesPointResponse;
import com.cosmate.repository.*;
import com.cosmate.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final CostumeRepository costumeRepository;
    private final OrderRepository orderRepository;
    private final DisputeRepository disputeRepository;
    private final WithdrawRequestRepository withdrawRequestRepository;
    private final ReviewRepository reviewRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;

    @Override
    public AdminDashboardSummaryResponse getSummary() {
        long totalUsers = userRepository.count();
        long totalProviders = providerRepository.count();
        long totalCostumes = costumeRepository.count();
        long totalOrders = orderRepository.count();
        long openDisputes = disputeRepository.findByStatusOrderByCreatedAtDesc("OPEN").size();
        long pendingWithdraw = withdrawRequestRepository.findByStatus("PENDING").size();
        long reviewsToModerate = reviewRepository.count();

        BigDecimal revenueToday = orderRepository.findAll().stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().toLocalDate().isEqual(LocalDate.now()))
                .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal revenueThisMonth = orderRepository.findAll().stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().toLocalDate().getYear() == LocalDate.now().getYear()
                        && o.getCreatedAt().toLocalDate().getMonth() == LocalDate.now().getMonth())
                .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminDashboardSummaryResponse.builder()
                .totalUsers(totalUsers)
                .totalProviders(totalProviders)
                .totalCostumes(totalCostumes)
                .totalOrders(totalOrders)
                .openDisputes(openDisputes)
                .pendingWithdrawRequests(pendingWithdraw)
                .reviewsToModerate(reviewsToModerate)
                .revenueToday(revenueToday)
                .revenueThisMonth(revenueThisMonth)
                .build();
    }

    @Override
    public List<AdminReportSeriesPointResponse> getRevenueReport() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        return orderRepository.findAll().stream()
                .filter(o -> o.getCreatedAt() != null)
                .collect(java.util.stream.Collectors.groupingBy(o -> o.getCreatedAt().format(fmt),
                        java.util.stream.Collectors.reducing(BigDecimal.ZERO,
                                o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount(), BigDecimal::add)))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> AdminReportSeriesPointResponse.builder().label(e.getKey()).value(e.getValue()).build())
                .toList();
    }

    @Override
    public List<AdminReportSeriesPointResponse> getOrdersReport() {
        return orderRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(o -> o.getStatus() == null ? "UNKNOWN" : o.getStatus(), java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> AdminReportSeriesPointResponse.builder().label(e.getKey()).value(BigDecimal.valueOf(e.getValue())).build())
                .toList();
    }

    @Override
    public List<AdminReportSeriesPointResponse> getUsersReport() {
        return userRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(u -> u.getStatus() == null ? "UNKNOWN" : u.getStatus(), java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> AdminReportSeriesPointResponse.builder().label(e.getKey()).value(BigDecimal.valueOf(e.getValue())).build())
                .toList();
    }

    @Override
    public List<AdminReportSeriesPointResponse> getProvidersReport() {
        return providerRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(p -> p.getVerified() != null && p.getVerified() ? "VERIFIED" : "PENDING", java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> AdminReportSeriesPointResponse.builder().label(e.getKey()).value(BigDecimal.valueOf(e.getValue())).build())
                .toList();
    }

    @Override
    public List<AdminReportSeriesPointResponse> getDisputesReport() {
        return disputeRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(d -> d.getStatus() == null ? "UNKNOWN" : d.getStatus(), java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> AdminReportSeriesPointResponse.builder().label(e.getKey()).value(BigDecimal.valueOf(e.getValue())).build())
                .toList();
    }

    @Override
    public List<com.cosmate.dto.response.AdminAuditLogResponse> getAuditLogs() {
        return adminAuditLogRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(
                        com.cosmate.entity.AdminAuditLog::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())
                ).reversed())
                .map(log -> com.cosmate.dto.response.AdminAuditLogResponse.builder()
                        .id(String.valueOf(log.getId()))
                        .actor(log.getActor())
                        .action(log.getAction())
                        .entityType(log.getEntityType())
                        .entityId(log.getEntityId())
                        .detail(log.getDetail())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();
    }
}
