package com.cosmate.service.impl;

import com.cosmate.dto.request.CreateOrderRequest;
import com.cosmate.dto.response.OrderResponse;
import com.cosmate.entity.*;
import com.cosmate.exception.InsufficientBalanceException;
import com.cosmate.repository.OrderCostumeSurchargeRepository;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.OrderDetailRepository;
import com.cosmate.repository.OrderRepository;
import com.cosmate.repository.TransactionRepository;
import com.cosmate.service.OrderService;
import com.cosmate.service.VnPayService;
import com.cosmate.service.WalletService;
import com.cosmate.service.MomoService;
import com.cosmate.repository.OrderAddressRepository;
import com.cosmate.repository.AddressRepository;
import com.cosmate.service.ProviderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CostumeRepository costumeRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final VnPayService vnPayService;
    private final MomoService momoService;
    private final AddressRepository addressRepository;
    private final OrderAddressRepository orderAddressRepository;
    private final ProviderService providerService;
    private final OrderCostumeSurchargeRepository orderCostumeSurchargeRepository;

    // New repositories for mapping costume data into order-specific tables
    private final com.cosmate.repository.CostumeAccessoryRepository costumeAccessoryRepository;
    private final com.cosmate.repository.CostumeRentalOptionRepository costumeRentalOptionRepository;
    private final com.cosmate.repository.OrderDetailAccessoryRepository orderDetailAccessoryRepository;
    private final com.cosmate.repository.OrderRentalOptionRepository orderRentalOptionRepository;

    @Override
    @Transactional
    public OrderResponse createOrder(Integer cosplayerId, CreateOrderRequest request) throws Exception {
        // Validate single costume per order
        if (request.getCostumeId() == null) throw new IllegalArgumentException("costumeId is required");
        if (request.getRentDay() == null || request.getRentDay() <= 0)
            throw new IllegalArgumentException("rentDay must be greater than 0");

        if (request.getSelectedRentalOptionId() == null) {
            throw new IllegalArgumentException("selectedRentalOptionId is required (one rental option must be chosen)");
        }

        LocalDateTime rentStart;
        try {
            rentStart = LocalDateTime.parse(request.getRentStart(), DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid rentStart format. Use ISO date-time, e.g. 2026-02-15T10:00:00");
        }

        // fetch the single costume
        Optional<Costume> co = costumeRepository.findById(request.getCostumeId());
        if (co.isEmpty()) throw new IllegalArgumentException("Costume id " + request.getCostumeId() + " not found");
        Costume c = co.get();

        // provider check (single costume => provider is that costume's provider)
        Integer providerId = c.getProviderId();

        int rentDay = request.getRentDay();

        // compute base rent and deposit
        BigDecimal pricePerDay = c.getPricePerDay() == null ? BigDecimal.ZERO : c.getPricePerDay();
        BigDecimal rentAmount = pricePerDay.multiply(new BigDecimal(rentDay));
        BigDecimal deposit = c.getDepositAmount() == null ? BigDecimal.ZERO : c.getDepositAmount();

        // sum surcharges for this costume
        BigDecimal surchargeSum = BigDecimal.ZERO;
        if (c.getSurcharges() != null) {
            for (CostumeSurcharge cs : c.getSurcharges()) {
                BigDecimal p = cs.getPrice() == null ? BigDecimal.ZERO : cs.getPrice();
                surchargeSum = surchargeSum.add(p);
            }
        }

        // process selected accessories (0..N)
        BigDecimal accessoriesSum = BigDecimal.ZERO;
        List<Integer> selectedAcc = request.getSelectedAccessoryIds();
        if (selectedAcc != null && !selectedAcc.isEmpty()) {
            for (Integer accId : selectedAcc) {
                Optional<CostumeAccessory> accOpt = costumeAccessoryRepository.findById(accId);
                if (accOpt.isEmpty()) throw new IllegalArgumentException("Accessory id " + accId + " not found");
                CostumeAccessory acc = accOpt.get();
                if (acc.getCostume() == null || !acc.getCostume().getId().equals(c.getId())) {
                    throw new IllegalArgumentException("Accessory id " + accId + " does not belong to costume " + c.getId());
                }
                accessoriesSum = accessoriesSum.add(acc.getPrice() == null ? BigDecimal.ZERO : acc.getPrice());
            }
        }

        // process selected rental option (exactly 1)
        Integer selectedRentalOptionId = request.getSelectedRentalOptionId();
        Optional<CostumeRentalOption> ropt = costumeRentalOptionRepository.findById(selectedRentalOptionId);
        if (ropt.isEmpty()) throw new IllegalArgumentException("Rental option id " + selectedRentalOptionId + " not found");
        CostumeRentalOption roption = ropt.get();
        if (roption.getCostume() == null || !roption.getCostume().getId().equals(c.getId())) {
            throw new IllegalArgumentException("Rental option id " + selectedRentalOptionId + " does not belong to costume " + c.getId());
        }
        BigDecimal rentalOptionPrice = roption.getPrice() == null ? BigDecimal.ZERO : roption.getPrice();

        // compute totals
        BigDecimal totalRent = rentAmount.add(surchargeSum).add(accessoriesSum).add(rentalOptionPrice);
        BigDecimal totalDeposit = deposit;
        BigDecimal totalAmount = totalRent.add(totalDeposit);

        // Create Order
        Order order = Order.builder()
                .cosplayerId(cosplayerId)
                .providerId(providerId)
                .orderType("RENT_COSTUME")
                .status("UNPAID")
                .totalAmount(totalAmount)
                .createdAt(LocalDateTime.now())
                .build();
        order = orderRepository.save(order);
        final Integer savedOrderId = order.getId();

        // Create OrderDetail for the single costume
        OrderDetail od = OrderDetail.builder()
                .orderId(order.getId())
                .costumeId(c.getId())
                .size(c.getSize())
                .numberOfItems(c.getNumberOfItems())
                .rentDay(rentDay)
                .rentStart(rentStart)
                .rentEnd(rentStart.plusDays(rentDay))
                .depositAmount(deposit)
                .rentAmount(rentAmount)
                .surchargeAmount(surchargeSum)
                .accessoriesAmount(accessoriesSum)
                .rentOptionAmount(rentalOptionPrice)
                .build();
        orderDetailRepository.save(od);

        // persist accessories into OrderDetailAccessory
        if (selectedAcc != null && !selectedAcc.isEmpty()) {
            for (Integer accId : selectedAcc) {
                CostumeAccessory acc = costumeAccessoryRepository.findById(accId).get();
                OrderDetailAccessory oda = OrderDetailAccessory.builder()
                        .orderDetail(od)
                        .accessoryName(acc.getName())
                        .accessoryDescription(acc.getDescription())
                        .price(acc.getPrice())
                        .build();
                orderDetailAccessoryRepository.save(oda);
            }
        }

        // persist chosen rental option into OrderRentalOption table
        OrderRentalOption oro = OrderRentalOption.builder()
                .orderDetail(od)
                .optionName(roption.getName())
                .price(rentalOptionPrice)
                .description(roption.getDescription())
                .build();
        orderRentalOptionRepository.save(oro);

        // persist order-level surcharge entries
        if (c.getSurcharges() != null) {
            for (CostumeSurcharge cs : c.getSurcharges()) {
                OrderCostumeSurcharge ocs = OrderCostumeSurcharge.builder()
                        .orderId(order.getId())
                        .costumeId(c.getId())
                        .name(cs.getName())
                        .description(cs.getDescription())
                        .price(cs.getPrice())
                        .build();
                orderCostumeSurchargeRepository.save(ocs);
            }
        }

        // update costume status
        c.setStatus("RENTED");
        costumeRepository.save(c);

        // store addresses: cosplayer selected address + provider shop address (if exists)
        Integer cosplayerAddrId = request.getCosplayerAddressId();
        if (cosplayerAddrId != null) {
            addressRepository.findById(cosplayerAddrId).ifPresent(a -> {
                OrderAddress oa = OrderAddress.builder()
                        .orderId(savedOrderId)
                        .addressFrom("COSPLAYER")
                        .name(a.getName())
                        .city(a.getCity())
                        .district(a.getDistrict())
                        .address(a.getAddress())
                        .phone(a.getPhone())
                        .build();
                orderAddressRepository.save(oa);
            });
        }

        // save provider address if provider has shopAddressId
        Provider prov = null;
        try {
            prov = providerService.getById(providerId);
        } catch (Exception e1) {
            try {
                prov = providerService.getByUserId(providerId);
            } catch (Exception e2) {
                prov = null;
            }
        }
        if (prov != null) {
            Integer shopAddrId = prov.getShopAddressId();
            if (shopAddrId != null) {
                addressRepository.findById(shopAddrId).ifPresent(a -> {
                    OrderAddress oa = OrderAddress.builder()
                            .orderId(savedOrderId)
                            .addressFrom("PROVIDER")
                            .name(a.getName())
                            .city(a.getCity())
                            .district(a.getDistrict())
                            .address(a.getAddress())
                            .phone(a.getPhone())
                            .build();
                    orderAddressRepository.save(oa);
                });
            } else {
                if (prov.getUserId() != null) {
                    var addrs = addressRepository.findAllByUserId(prov.getUserId());
                    if (addrs != null && !addrs.isEmpty()) {
                        var a = addrs.get(0);
                        OrderAddress oa = OrderAddress.builder()
                                .orderId(savedOrderId)
                                .addressFrom("PROVIDER")
                                .name(a.getName())
                                .city(a.getCity())
                                .district(a.getDistrict())
                                .address(a.getAddress())
                                .phone(a.getPhone())
                                .build();
                        orderAddressRepository.save(oa);
                    }
                }
            }
        }

        // Create transaction record depending on payment method
        String pm = request.getPaymentMethod();
        if (pm == null) pm = "WALLET";

        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setCosplayerId(order.getCosplayerId());
        resp.setProviderId(order.getProviderId());
        resp.setOrderType(order.getOrderType());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCreatedAt(order.getCreatedAt());

        if ("WALLET".equalsIgnoreCase(pm)) {
            // debit user's wallet
            com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(cosplayerId).build();
            Wallet wallet = walletService.createForUser(u);
            walletService.debit(wallet, totalAmount, "Order payment", "ORDER" + order.getId());

            // mark order paid
            order.setStatus("PAID");
            orderRepository.save(order);
            resp.setStatus(order.getStatus());
            return resp;
        }

        // For VNPay and MOMO: create PENDING transaction and return the payment url
        if ("VNPay".equalsIgnoreCase(pm) || "MOMO".equalsIgnoreCase(pm)) {
            com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(cosplayerId).build();
            Wallet wallet = walletService.createForUser(u);
            Transaction pending = Transaction.builder()
                    .wallet(wallet)
                    .amount(totalAmount)
                    .type("ORDER#" + order.getId())
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();
            pending = transactionRepository.save(pending);

            // build return URL
            String returnUrl = request.getReturnUrl();
            if (returnUrl == null || returnUrl.isEmpty()) {
                // default return url - caller should provide in request for real flows
                returnUrl = "/api/payments/vnpay-return";
            }

            String paymentUrl;
            if ("MOMO".equalsIgnoreCase(pm)) {
                paymentUrl = momoService.createPaymentUrlForTransaction(cosplayerId, totalAmount, returnUrl, pending.getId());
            } else {
                paymentUrl = vnPayService.createPaymentUrlForTransaction(cosplayerId, totalAmount, returnUrl, pending.getId());
            }
            resp.setPaymentUrl(paymentUrl);
            return resp;
        }

        // unsupported payment method
        throw new IllegalArgumentException("Unsupported payment method: " + pm);
    }

    @Override
    @Transactional
    public OrderResponse payOrder(Integer cosplayerId, Integer orderId, String paymentMethod, String returnUrl) throws Exception {
        Optional<Order> opt = orderRepository.findById(orderId);
        if (opt.isEmpty()) throw new IllegalArgumentException("Order not found");
        Order order = opt.get();
        if (!order.getCosplayerId().equals(cosplayerId)) throw new IllegalArgumentException("Order does not belong to user");
        if (!"UNPAID".equalsIgnoreCase(order.getStatus())) throw new IllegalArgumentException("Order is not in UNPAID status");

        BigDecimal totalAmount = order.getTotalAmount();
        if (totalAmount == null) totalAmount = BigDecimal.ZERO;

        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setCosplayerId(order.getCosplayerId());
        resp.setProviderId(order.getProviderId());
        resp.setOrderType(order.getOrderType());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(totalAmount);
        resp.setCreatedAt(order.getCreatedAt());

        String pm = paymentMethod == null ? "WALLET" : paymentMethod;
        if ("WALLET".equalsIgnoreCase(pm)) {
            com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(cosplayerId).build();
            Wallet wallet = walletService.createForUser(u);
            try {
                walletService.debit(wallet, totalAmount, "Order payment", "ORDER" + order.getId());
            } catch (InsufficientBalanceException ex) {
                throw ex;
            }
            order.setStatus("PAID");
            orderRepository.save(order);
            resp.setStatus(order.getStatus());
            return resp;
        }

        // VNPay or Momo
        com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(cosplayerId).build();
        Wallet wallet = walletService.createForUser(u);
        Transaction pending = Transaction.builder()
                .wallet(wallet)
                .amount(totalAmount)
                .type("ORDER#" + order.getId())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        pending = transactionRepository.save(pending);

        if (returnUrl == null || returnUrl.isEmpty()) returnUrl = "/api/payments/vnpay-return";

        String paymentUrl;
        if ("MOMO".equalsIgnoreCase(pm)) {
            paymentUrl = momoService.createPaymentUrlForTransaction(cosplayerId, totalAmount, returnUrl, pending.getId());
        } else {
            paymentUrl = vnPayService.createPaymentUrlForTransaction(cosplayerId, totalAmount, returnUrl, pending.getId());
        }
        resp.setPaymentUrl(paymentUrl);
        return resp;
    }
}
