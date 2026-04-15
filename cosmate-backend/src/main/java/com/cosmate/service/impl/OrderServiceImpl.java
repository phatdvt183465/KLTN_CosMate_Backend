package com.cosmate.service.impl;

import com.cosmate.dto.request.CreateOrderRequest;
import com.cosmate.dto.response.OrderDropdownResponse;
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
import java.math.RoundingMode;
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
     private final com.cosmate.repository.ServiceRepository serviceRepository;
    private final com.cosmate.repository.OrderServiceBookingRepository orderServiceBookingRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final VnPayService vnPayService;
    private final MomoService momoService;
    private final com.cosmate.service.NotificationService notificationService;
    private final com.cosmate.repository.WishlistRepository wishlistRepository;
    private final AddressRepository addressRepository;
    private final OrderAddressRepository orderAddressRepository;
    private final ProviderService providerService;
    private final OrderCostumeSurchargeRepository orderCostumeSurchargeRepository;

    // New repositories for mapping costume data into order-specific tables
    private final com.cosmate.repository.CostumeAccessoryRepository costumeAccessoryRepository;
    private final com.cosmate.repository.CostumeRentalOptionRepository costumeRentalOptionRepository;
    private final com.cosmate.repository.OrderDetailAccessoryRepository orderDetailAccessoryRepository;
    private final com.cosmate.repository.OrderRentalOptionRepository orderRentalOptionRepository;
    private final com.cosmate.repository.OrderImageRepository orderImageRepository;
    private final com.cosmate.repository.OrderTrackingRepository orderTrackingRepository;
    private final com.cosmate.service.FirebaseStorageService firebaseStorageService;

    private final com.cosmate.repository.OrderDetailExtendRepository orderDetailExtendRepository;

    // user repository used for wallet creation fallback
    private final com.cosmate.repository.UserRepository userRepository;


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
        BigDecimal deposit = c.getDepositAmount() == null ? BigDecimal.ZERO : c.getDepositAmount();

        // rentDiscount is a percentage (0..100) representing how much to charge for each day from day 2 onward
        // default to 100% (no discount) when costume record has null rentDiscount
        Integer rentDiscountInt = c.getRentDiscount() == null ? 100 : c.getRentDiscount();
        BigDecimal rentDiscountPct = new BigDecimal(rentDiscountInt);

        BigDecimal rentAmount;
        if (rentDay <= 1) {
            rentAmount = pricePerDay;
        } else {
            // first day full price
            BigDecimal firstDay = pricePerDay;
            // subsequent days use rentDiscount percent of pricePerDay
            BigDecimal subsequentRate = pricePerDay.multiply(rentDiscountPct).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            BigDecimal subsequentTotal = subsequentRate.multiply(new BigDecimal(rentDay - 1));
            rentAmount = firstDay.add(subsequentTotal);
        }

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
                .totalDepositAmount(totalDeposit)
                .createdAt(LocalDateTime.now())
                .rentDate(rentStart)
                .build();
        order = orderRepository.save(order);
        final Integer savedOrderId = order.getId();

        // notify provider that a new order has been created (UNPAID)
        try {
            // try to fetch provider user id
            com.cosmate.entity.Provider provEntity = null;
            try { provEntity = providerService.getById(providerId); } catch (Exception ignored) { provEntity = null; }
            Integer providerUserId = (provEntity != null) ? provEntity.getUserId() : null;
            if (providerUserId != null) {
                com.cosmate.entity.Notification pn = com.cosmate.entity.Notification.builder()
                        .user(com.cosmate.entity.User.builder().id(providerUserId).build())
                        .type("ORDER_STATUS")
                        .header("Đơn hàng mới")
                        .content("Đơn hàng #" + order.getId() + " đã được tạo và đang chờ xử lý (UNPAID).")
                        .sendAt(LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(pn);
            }
        } catch (Exception ignored) {}

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
                .rentDiscount(rentDiscountInt)
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
        resp.setTotalDepositAmount(order.getTotalDepositAmount());
        resp.setCreatedAt(order.getCreatedAt());
        resp.setRentDate(order.getRentDate());

        if ("WALLET".equalsIgnoreCase(pm)) {
            // debit user's wallet
            com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(cosplayerId).build();
            Wallet wallet = walletService.createForUser(u);
            walletService.debit(wallet, totalAmount, "Order payment", "ORDER" + order.getId(), "WALLET", order);

            // mark order paid
            order.setStatus("PAID");
            orderRepository.save(order);
            // Gửi notification cho người dùng
            try {
                com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                        .user(com.cosmate.entity.User.builder().id(cosplayerId).build())
                        .type("ORDER_STATUS")
                        .header("Đơn hàng đã được thanh toán")
                        .content("Đơn hàng #" + order.getId() + " đã được thanh toán thành công.")
                        .sendAt(LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(n);
                // notify provider as well
                try {
                    com.cosmate.entity.Provider provEntity = null;
                    try { provEntity = providerService.getById(providerId); } catch (Exception ignored2) { provEntity = null; }
                    Integer provUserId = provEntity == null ? null : provEntity.getUserId();
                    if (provUserId != null) {
                        com.cosmate.entity.Notification pn = com.cosmate.entity.Notification.builder()
                                .user(com.cosmate.entity.User.builder().id(provUserId).build())
                                .type("ORDER_STATUS")
                                .header("Đơn hàng đã được thanh toán")
                                .content("Đơn hàng #" + order.getId() + " đã được khách hàng thanh toán.")
                                .sendAt(LocalDateTime.now())
                                .isRead(false)
                                .build();
                        notificationService.create(pn);
                    }
                } catch (Exception ignored2) {}
            } catch (Exception ignored) {}
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
                    .order(order)
                    .paymentMethod(pm)
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

    // --- Service-order operations moved from ServiceOrderController ---
    @Override
    @Transactional
    public OrderResponse providerCreateBooking(Integer providerUserId, com.cosmate.dto.request.CreateServiceOrderRequest req) throws Exception {
        // validate provider
        com.cosmate.entity.Provider prov = providerService.getByUserId(providerUserId);
        if (prov == null) throw new IllegalArgumentException("User is not a provider");

        if (req.getServiceId() == null) throw new IllegalArgumentException("serviceId is required");
        Optional<com.cosmate.entity.Service> sopt = serviceRepository.findById(req.getServiceId());
        if (sopt.isEmpty()) throw new IllegalArgumentException("Service not found");
        com.cosmate.entity.Service s = sopt.get();
        if (!prov.getId().equals(s.getProviderId())) throw new IllegalArgumentException("Provider does not own the service");

        Integer cosplayerId = req.getCosplayerId();
        if (cosplayerId == null) throw new IllegalArgumentException("cosplayerId is required");

        java.time.LocalDate bookingDate = null;
        try { bookingDate = java.time.LocalDate.parse(req.getBookingDate()); } catch (DateTimeParseException ex) { throw new IllegalArgumentException("Invalid bookingDate format, expected yyyy-MM-dd"); }

        java.math.BigDecimal rent = req.getRentSlotAmount() == null ? java.math.BigDecimal.ZERO : req.getRentSlotAmount();
        java.math.BigDecimal deposit = s.getDepositAmount() == null ? java.math.BigDecimal.ZERO : s.getDepositAmount();
        java.math.BigDecimal total = rent.add(deposit);

        Order order = Order.builder()
                .cosplayerId(cosplayerId)
                .providerId(prov.getId())
                .orderType("RENT_SERVICE")
                .status("UNCONFIRM")
                .totalAmount(total)
                .totalDepositAmount(deposit)
                .createdAt(java.time.LocalDateTime.now())
                .rentDate(java.time.LocalDateTime.of(bookingDate, java.time.LocalTime.MIDNIGHT))
                .build();
        order = orderRepository.save(order);

        OrderServiceBooking osb = OrderServiceBooking.builder()
                .orderId(order.getId())
                .serviceId(s.getId())
                .bookingDate(bookingDate)
                .timeSlot(req.getTimeSlot())
                .numberOfHuman(req.getNumberOfHuman())
                .depositSlotAmount(deposit)
                .rentSlotAmount(rent)
                .build();
        orderServiceBookingRepository.save(osb);

        // notify cosplayer
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(cosplayerId).build())
                    .type("ORDER_STATUS")
                    .header("Nhà cung cấp đã tạo lịch dịch vụ")
                    .content("Nhà cung cấp đã tạo lịch cho dịch vụ #" + s.getId() + " cho đơn hàng #" + order.getId() + ". Vui lòng xác nhận.")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}

        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setCosplayerId(order.getCosplayerId());
        resp.setProviderId(order.getProviderId());
        resp.setOrderType(order.getOrderType());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCreatedAt(order.getCreatedAt());
        resp.setRentDate(order.getRentDate());
        return resp;
    }

    @Override
    @Transactional
    public OrderResponse confirmServiceOrderByCosplayer(Integer cosplayerUserId, Integer orderId) throws Exception {
        Optional<Order> opt = orderRepository.findById(orderId);
        if (opt.isEmpty()) throw new IllegalArgumentException("Order not found");
        Order order = opt.get();
        if (!order.getCosplayerId().equals(cosplayerUserId)) throw new IllegalArgumentException("Order does not belong to user");
        if (!"UNCONFIRM".equals(order.getStatus())) throw new IllegalArgumentException("Order is not in UNCONFIRM status");
        order.setStatus("UNPAID");
        orderRepository.save(order);
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                    .type("ORDER_STATUS")
                    .header("Đơn hàng đã được xác nhận")
                    .content("Đơn hàng #" + order.getId() + " đã được xác nhận và chuyển sang UNPAID.")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}

        try {
            com.cosmate.entity.Provider p = null;
            try { p = providerService.getById(order.getProviderId()); } catch (Exception ignored2) { p = null; }
            Integer providerUserId = p == null ? null : p.getUserId();
            if (providerUserId != null) {
                com.cosmate.entity.Notification pn = com.cosmate.entity.Notification.builder()
                        .user(com.cosmate.entity.User.builder().id(providerUserId).build())
                        .type("ORDER_STATUS")
                        .header("Đơn hàng đã được xác nhận bởi khách")
                        .content("Khách hàng đã xác nhận đơn hàng #" + order.getId() + ". Vui lòng chuẩn bị dịch vụ.")
                        .sendAt(java.time.LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(pn);
            }
        } catch (Exception ignored) {}

        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setCosplayerId(order.getCosplayerId());
        resp.setProviderId(order.getProviderId());
        resp.setOrderType(order.getOrderType());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCreatedAt(order.getCreatedAt());
        resp.setRentDate(order.getRentDate());
        return resp;
    }

    @Override
    @Transactional
    public OrderResponse providerSetWaiting(Integer providerUserId, Integer orderId) throws Exception {
        com.cosmate.entity.Provider prov = providerService.getByUserId(providerUserId);
        if (prov == null) throw new IllegalArgumentException("User is not a provider");
        Optional<Order> opt = orderRepository.findById(orderId);
        if (opt.isEmpty()) throw new IllegalArgumentException("Order not found");
        Order order = opt.get();
        if (!order.getProviderId().equals(prov.getId())) throw new IllegalArgumentException("Provider does not own this order");
        if (!"PAID".equals(order.getStatus())) throw new IllegalArgumentException("Order must be in PAID status to set WAITING_SERVICE_DATE");
        order.setStatus("WAITING_SERVICE_DATE");
        orderRepository.save(order);
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                    .type("ORDER_STATUS")
                    .header("Đơn hàng đã được lên lịch")
                    .content("Đơn hàng #" + order.getId() + " đã được đặt lịch (WAITING_SERVICE_DATE).")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}
        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setCosplayerId(order.getCosplayerId());
        resp.setProviderId(order.getProviderId());
        resp.setOrderType(order.getOrderType());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCreatedAt(order.getCreatedAt());
        resp.setRentDate(order.getRentDate());
        return resp;
    }

    @Override
    @Transactional
    public OrderResponse startServiceNow(Integer providerUserId, Integer orderId) throws Exception {
        com.cosmate.entity.Provider prov = providerService.getByUserId(providerUserId);
        if (prov == null) throw new IllegalArgumentException("User is not a provider");
        Optional<Order> opt = orderRepository.findById(orderId);
        if (opt.isEmpty()) throw new IllegalArgumentException("Order not found");
        Order order = opt.get();
        if (!order.getProviderId().equals(prov.getId())) throw new IllegalArgumentException("Provider does not own this order");
        if (!"WAITING_SERVICE_DATE".equals(order.getStatus())) throw new IllegalArgumentException("Order must be in WAITING_SERVICE_DATE status to start service");
        java.util.List<OrderServiceBooking> bookings = orderServiceBookingRepository.findByOrderId(order.getId());
        if (bookings.isEmpty()) throw new IllegalArgumentException("No service booking found for order");
        OrderServiceBooking osb = bookings.get(0);
        java.time.LocalDate today = java.time.LocalDate.now();
        if (osb.getBookingDate() != null && osb.getBookingDate().isAfter(today)) throw new IllegalArgumentException("Booking date is in the future; cannot start service yet");
        order.setStatus("IN_SERVICE");
        orderRepository.save(order);
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                    .type("ORDER_STATUS")
                    .header("Đơn hàng đang được thực hiện")
                    .content("Đơn hàng #" + order.getId() + " đã bắt đầu (IN_SERVICE).")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}
        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setCosplayerId(order.getCosplayerId());
        resp.setProviderId(order.getProviderId());
        resp.setOrderType(order.getOrderType());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCreatedAt(order.getCreatedAt());
        resp.setRentDate(order.getRentDate());
        return resp;
    }

    @Override
    @Transactional
    public OrderResponse providerCompleteService(Integer providerUserId, Integer orderId) throws Exception {
        com.cosmate.entity.Provider prov = providerService.getByUserId(providerUserId);
        if (prov == null) throw new IllegalArgumentException("User is not a provider");
        Optional<Order> opt = orderRepository.findById(orderId);
        if (opt.isEmpty()) throw new IllegalArgumentException("Order not found");
        Order order = opt.get();
        if (!order.getProviderId().equals(prov.getId())) throw new IllegalArgumentException("Provider does not own this order");
        if (!"IN_SERVICE".equals(order.getStatus())) throw new IllegalArgumentException("Order must be IN_SERVICE to be completed");
        order.setStatus("COMPLETED");
        orderRepository.save(order);
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                    .type("ORDER_STATUS")
                    .header("Dịch vụ hoàn tất")
                    .content("Đơn hàng #" + order.getId() + " dịch vụ đã hoàn tất (COMPLETED).")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}

        // Transfer money to provider's wallet when service completes
        try {
            Integer providerUserIdVal = prov.getUserId();
            java.util.Optional<com.cosmate.entity.Wallet> wopt = walletService.getByUserId(providerUserIdVal);
            if (wopt.isPresent()) {
                com.cosmate.entity.Wallet wallet = wopt.get();
                java.math.BigDecimal amount = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();
                walletService.credit(wallet, amount, "Payout for completed order", "ORDER_PAYOUT:" + order.getId(), null, order);
            } else {
                java.util.Optional<com.cosmate.entity.User> providerUserOpt = userRepository.findById(providerUserIdVal);
                if (providerUserOpt.isPresent()) {
                    walletService.createForUser(providerUserOpt.get());
                    java.util.Optional<com.cosmate.entity.Wallet> wopt2 = walletService.getByUserId(providerUserIdVal);
                    if (wopt2.isPresent()) {
                        com.cosmate.entity.Wallet wallet = wopt2.get();
                        java.math.BigDecimal amount = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();
                        walletService.credit(wallet, amount, "Payout for completed order", "ORDER_PAYOUT:" + order.getId(), null, order);
                    }
                }
            }
        } catch (Exception ex) {
            // swallow
        }

        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setCosplayerId(order.getCosplayerId());
        resp.setProviderId(order.getProviderId());
        resp.setOrderType(order.getOrderType());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCreatedAt(order.getCreatedAt());
        resp.setRentDate(order.getRentDate());
        return resp;
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Integer userId, Integer orderId) throws Exception {
        Optional<Order> opt = orderRepository.findById(orderId);
        if (opt.isEmpty()) throw new IllegalArgumentException("Order not found");
        Order order = opt.get();

        boolean isCosplayer = order.getCosplayerId() != null && order.getCosplayerId().equals(userId);
        // providerService.getByUserId throws when the user is not a provider (AppException).
        // Safely attempt to fetch provider record and treat missing provider as null so cosplayers can cancel.
        com.cosmate.entity.Provider prov = null;
        try { prov = providerService.getByUserId(userId); } catch (Exception ignored) { prov = null; }
        boolean isProviderOwner = prov != null && order.getProviderId() != null && order.getProviderId().equals(prov.getId());
        if (!isCosplayer && !isProviderOwner) throw new IllegalArgumentException("No permission to cancel this order");

        String status = order.getStatus();
        if (status == null) status = "";
        if (!(status.equals("UNCONFIRM") || status.equals("UNPAID") || status.equals("PAID") || status.equals("WAITING_SERVICE_DATE"))) {
            throw new IllegalArgumentException("Order cannot be cancelled in its current status: " + status);
        }

        if ("PAID".equals(status)) {
            if (order.getCosplayerId() == null) throw new IllegalArgumentException("Cosplayer info missing on order; cannot refund");
            java.util.Optional<com.cosmate.entity.Wallet> wopt = walletService.getByUserId(order.getCosplayerId());
            if (wopt.isEmpty()) throw new IllegalArgumentException("Wallet for cosplayer not found; refund failed");
            com.cosmate.entity.Wallet wallet = wopt.get();
            java.math.BigDecimal amount = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();
            walletService.credit(wallet, amount, "Refund for cancelled order", "ORDER_REFUND:" + order.getId(), null, order);
        }

        order.setStatus("CANCELLED");
        orderRepository.save(order);

        // If this order included costumes, set their status back to AVAILABLE when the order is cancelled
        try {
            java.util.List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
            if (details != null && !details.isEmpty()) {
                for (OrderDetail d : details) {
                    if (d == null || d.getCostumeId() == null) continue;
                    Costume cc = costumeRepository.findById(d.getCostumeId()).orElse(null);
                    if (cc != null) {
                        cc.setStatus("AVAILABLE");
                        costumeRepository.save(cc);
                        try {
                            java.util.List<com.cosmate.entity.WishlistCostume> watchers = wishlistRepository.findAllByCostumeId(cc.getId());
                            if (watchers != null && !watchers.isEmpty()) {
                                for (com.cosmate.entity.WishlistCostume w : watchers) {
                                    try {
                                        com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                                .user(com.cosmate.entity.User.builder().id(w.getUserId()).build())
                                                .type("WISHLIST_NOTIFY")
                                                .header("Bộ đồ bạn quan tâm đã có sẵn")
                                                .content("Bộ đồ '" + cc.getName() + "' hiện đã có sẵn để thuê.")
                                                .sendAt(java.time.LocalDateTime.now())
                                                .isRead(false)
                                                .build();
                                        notificationService.create(n);
                                    } catch (Exception ignored) {}
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                    .type("ORDER_STATUS")
                    .header("Đơn hàng bị hủy")
                    .content("Đơn hàng #" + order.getId() + " đã bị hủy.")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}

        try {
            Integer providerUserId = null;
            try {
                com.cosmate.entity.Provider provEntity = providerService.getById(order.getProviderId());
                if (provEntity != null) providerUserId = provEntity.getUserId();
            } catch (Exception ignored2) { providerUserId = null; }
            if (providerUserId != null) {
                com.cosmate.entity.Notification pn = com.cosmate.entity.Notification.builder()
                        .user(com.cosmate.entity.User.builder().id(providerUserId).build())
                        .type("ORDER_STATUS")
                        .header("Đơn hàng bị hủy")
                        .content("Đơn hàng #" + order.getId() + " đã bị hủy.")
                        .sendAt(java.time.LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(pn);
            }
        } catch (Exception ignored) {}

        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setCosplayerId(order.getCosplayerId());
        resp.setProviderId(order.getProviderId());
        resp.setOrderType(order.getOrderType());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCreatedAt(order.getCreatedAt());
        resp.setRentDate(order.getRentDate());
        return resp;
    }

    @Override
    public java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse> listProviderServiceOrders(Integer providerUserId, String statuses) throws Exception {
        com.cosmate.entity.Provider prov = providerService.getByUserId(providerUserId);
        if (prov == null) throw new IllegalArgumentException("User is not a provider");
        java.util.List<String> statusList = null;
        if (statuses != null && !statuses.trim().isEmpty()) {
            statusList = java.util.Arrays.stream(statuses.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        java.util.List<Order> orders;
        if (statusList == null) orders = orderRepository.findByProviderIdOrderByCreatedAtDesc(prov.getId());
        else orders = orderRepository.findByProviderIdAndStatusInOrderByCreatedAtDesc(prov.getId(), statusList);

        java.util.List<Order> serviceOrders = orders.stream().filter(o -> "RENT_SERVICE".equals(o.getOrderType())).toList();
        java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse> respList = new java.util.ArrayList<>();
        for (Order o : serviceOrders) {
            com.cosmate.dto.response.ServiceOrderItemResponse item = new com.cosmate.dto.response.ServiceOrderItemResponse();
            item.setId(o.getId());
            item.setCosplayerId(o.getCosplayerId());
            item.setProviderId(o.getProviderId());
            item.setOrderType(o.getOrderType());
            item.setStatus(o.getStatus());
            item.setTotalAmount(o.getTotalAmount());
            item.setCreatedAt(o.getCreatedAt());
            item.setBookings(orderServiceBookingRepository.findByOrderId(o.getId()));
            respList.add(item);
        }
        return respList;
    }

    @Override
    public java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse> listCosplayerServiceOrders(Integer cosplayerUserId, String statuses) throws Exception {
        java.util.List<String> statusList = null;
        if (statuses != null && !statuses.trim().isEmpty()) {
            statusList = java.util.Arrays.stream(statuses.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        // make a final reference for use inside lambda expressions
        final java.util.List<String> finalStatusList = statusList;
        java.util.List<Order> orders = orderRepository.findByCosplayerIdOrderByCreatedAtDesc(cosplayerUserId);
        if (finalStatusList != null && !finalStatusList.isEmpty()) {
            orders = orders.stream().filter(o -> o.getStatus() != null && finalStatusList.contains(o.getStatus())).toList();
        }

        java.util.List<Order> serviceOrders = orders.stream().filter(o -> "RENT_SERVICE".equals(o.getOrderType())).toList();
        java.util.List<com.cosmate.dto.response.ServiceOrderItemResponse> respList = new java.util.ArrayList<>();
        for (Order o : serviceOrders) {
            com.cosmate.dto.response.ServiceOrderItemResponse item = new com.cosmate.dto.response.ServiceOrderItemResponse();
            item.setId(o.getId());
            item.setCosplayerId(o.getCosplayerId());
            item.setProviderId(o.getProviderId());
            item.setOrderType(o.getOrderType());
            item.setStatus(o.getStatus());
            item.setTotalAmount(o.getTotalAmount());
            item.setCreatedAt(o.getCreatedAt());
            item.setBookings(orderServiceBookingRepository.findByOrderId(o.getId()));
            respList.add(item);
        }
        return respList;
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
        resp.setTotalDepositAmount(order.getTotalDepositAmount());
        resp.setCreatedAt(order.getCreatedAt());

        String pm = paymentMethod == null ? "WALLET" : paymentMethod;
        if ("WALLET".equalsIgnoreCase(pm)) {
            com.cosmate.entity.User u = com.cosmate.entity.User.builder().id(cosplayerId).build();
            Wallet wallet = walletService.createForUser(u);
            try {
                walletService.debit(wallet, totalAmount, "Order payment", "ORDER" + order.getId(), "WALLET", order);
            } catch (InsufficientBalanceException ex) {
                throw ex;
            }
            order.setStatus("PAID");
            orderRepository.save(order);
            // tạo notification cho người dùng
            try {
                com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                        .user(com.cosmate.entity.User.builder().id(cosplayerId).build())
                        .type("ORDER_STATUS")
                        .header("Đơn hàng đã được thanh toán")
                        .content("Đơn hàng #" + order.getId() + " đã được thanh toán thành công.")
                        .sendAt(LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(n);
                // notify provider as well
                try {
                    com.cosmate.entity.Provider provEntity = null;
                    try { provEntity = providerService.getById(order.getProviderId()); } catch (Exception ignored2) { provEntity = null; }
                    Integer provUserId = provEntity == null ? null : provEntity.getUserId();
                    if (provUserId != null) {
                        com.cosmate.entity.Notification pn = com.cosmate.entity.Notification.builder()
                                .user(com.cosmate.entity.User.builder().id(provUserId).build())
                                .type("ORDER_STATUS")
                                .header("Đơn hàng đã được thanh toán")
                                .content("Đơn hàng #" + order.getId() + " đã được khách hàng thanh toán.")
                                .sendAt(LocalDateTime.now())
                                .isRead(false)
                                .build();
                        notificationService.create(pn);
                    }
                } catch (Exception ignored2) {}
            } catch (Exception ignored) {}
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
                .order(order)
                .paymentMethod(pm)
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

    @Override
    public List<OrderDropdownResponse> listOrdersForDropdown(String orderType, List<String> statuses, Integer providerId, Integer cosplayerId) {
        // Default status sets per order type
        final List<String> RENT_COSTUME_STATUSES = java.util.Arrays.asList(
                "UNPAID",
                "PAID",
                "PREPARING",
                "SHIPPING_OUT",
                "DELIVERING_OUT",
                "IN_USE",
                "SHIPPING_BACK",
                "COMPLETED",
                "DISPUTE",
                "CANCELLED",
                "EXTENDING"
        );
        final List<String> RENT_SERVICE_STATUSES = java.util.Arrays.asList(
                "UNPAID",
                "PAID",
                "WAITING_SERVICE_DATE",
                "IN_SERVICE",
                "COMPLETED",
                "DISPUTE",
                "CANCELLED"
        );

        // Determine an effectively-final statuses list to use in lambdas
        final List<String> effectiveStatuses;
        if (statuses == null || statuses.isEmpty()) {
            if (orderType == null || orderType.isEmpty()) {
                // union of both lists (remove duplicates)
                java.util.Set<String> set = new java.util.LinkedHashSet<>();
                set.addAll(RENT_COSTUME_STATUSES);
                set.addAll(RENT_SERVICE_STATUSES);
                effectiveStatuses = new java.util.ArrayList<>(set);
            } else if ("RENT_COSTUME".equalsIgnoreCase(orderType)) {
                effectiveStatuses = RENT_COSTUME_STATUSES;
            } else if ("RENT_SERVICE".equalsIgnoreCase(orderType)) {
                effectiveStatuses = RENT_SERVICE_STATUSES;
            } else {
                effectiveStatuses = new java.util.ArrayList<>();
            }
        } else {
            effectiveStatuses = statuses;
        }

        List<Order> orders = new ArrayList<>();
        if (providerId != null && providerId > 0) {
            if (orderType != null && !orderType.isEmpty()) {
                orders = orderRepository.findByProviderIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(providerId, orderType, effectiveStatuses);
            } else {
                // fallback: if no orderType provided, get by provider and statuses using existing method by providerId and statuses
                orders = orderRepository.findByProviderIdAndStatusInOrderByCreatedAtDesc(providerId, effectiveStatuses);
            }
        } else if (cosplayerId != null && cosplayerId > 0) {
            if (orderType != null && !orderType.isEmpty()) {
                orders = orderRepository.findByCosplayerIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(cosplayerId, orderType, effectiveStatuses);
            } else {
                orders = orderRepository.findByCosplayerIdOrderByCreatedAtDesc(cosplayerId);
                // filter by statuses if provided
                if (effectiveStatuses != null && !effectiveStatuses.isEmpty()) {
                    orders.removeIf(o -> o.getStatus() == null || !effectiveStatuses.contains(o.getStatus()));
                }
            }
        } else {
            // global listing by orderType + statuses
            if (orderType != null && !orderType.isEmpty()) {
                orders = orderRepository.findByOrderTypeAndStatusInOrderByCreatedAtDesc(orderType, effectiveStatuses);
            } else {
                orders = orderRepository.findAllByOrderByCreatedAtDesc();
                if (effectiveStatuses != null && !effectiveStatuses.isEmpty()) {
                    orders.removeIf(o -> o.getStatus() == null || !effectiveStatuses.contains(o.getStatus()));
                }
            }
        }

        List<OrderDropdownResponse> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        for (Order o : orders) {
            String datePart = (o.getCreatedAt() != null) ? o.getCreatedAt().format(fmt) : "";
            String label = "#" + o.getId() + " - " + datePart + " - " + o.getStatus();
            result.add(new OrderDropdownResponse(o.getId(), label));
        }
        return result;
    }

    // --- Full order operations ---
    @Override
    public com.cosmate.dto.response.OrderFullResponse getFullOrderById(Integer id) throws Exception {
        Order o = orderRepository.findById(id).orElse(null);
        if (o == null) return null;
        com.cosmate.dto.response.OrderFullResponse resp = new com.cosmate.dto.response.OrderFullResponse();
        resp.setId(o.getId());
        resp.setCosplayerId(o.getCosplayerId());
        resp.setProviderId(o.getProviderId());
        resp.setOrderType(o.getOrderType());
        resp.setStatus(o.getStatus());
        resp.setTotalAmount(o.getTotalAmount());
        resp.setTotalDepositAmount(o.getTotalDepositAmount());
        resp.setCreatedAt(o.getCreatedAt());

        List<OrderDetail> details = orderDetailRepository.findByOrderId(o.getId());
        resp.setDetails(details);
        resp.setSurcharges(orderCostumeSurchargeRepository.findByOrderId(o.getId()));
        resp.setAddresses(orderAddressRepository.findByOrderId(o.getId()));
        List<Integer> detailIds = details.stream().map(d -> d.getId()).toList();
        resp.setAccessories(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderDetailAccessoryRepository.findByOrderDetailIdIn(detailIds));
        resp.setRentalOptions(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderRentalOptionRepository.findByOrderDetailIdIn(detailIds));
        resp.setImages(detailIds.isEmpty() ? java.util.Collections.emptyList() : orderImageRepository.findByOrderDetailIdIn(detailIds));
        resp.setTrackings(orderTrackingRepository.findByOrderId(o.getId()));

        // include any OrderDetailExtend records related to each order detail
        java.util.List<com.cosmate.entity.OrderDetailExtend> exts = new java.util.ArrayList<>();
        if (detailIds != null && !detailIds.isEmpty()) {
            for (Integer did : detailIds) {
                try {
                    java.util.List<com.cosmate.entity.OrderDetailExtend> found = orderDetailExtendRepository.findByOrderDetailId(did);
                    if (found != null && !found.isEmpty()) exts.addAll(found);
                } catch (Exception ignored) {}
            }
        }
        resp.setDetailExtends(exts);
        java.util.List<com.cosmate.entity.Transaction> txs = transactionRepository.findByOrder_IdOrderByCreatedAtDesc(o.getId());
        java.util.List<com.cosmate.dto.response.TransactionResponse> txResp = txs.stream().map(t -> com.cosmate.dto.response.TransactionResponse.builder()
                .id(t.getId())
                .amount(t.getAmount())
                .type(t.getType())
                .status(t.getStatus())
                .paymentMethod(t.getPaymentMethod())
                .walletId(t.getWallet() == null ? null : t.getWallet().getWalletId())
                .orderId(t.getOrder() == null ? null : t.getOrder().getId())
                .createdAt(t.getCreatedAt())
                .build()).toList();
        resp.setTransactions(txResp);
        return resp;
    }

    @Override
    public java.util.List<com.cosmate.dto.response.OrderFullResponse> listAllOrders() throws Exception {
        List<Order> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        List<com.cosmate.dto.response.OrderFullResponse> resp = orders.stream().map(this::mapToFullResponseSafe).filter(x -> x != null).toList();
        return resp;
    }

    private com.cosmate.dto.response.OrderFullResponse mapToFullResponseSafe(Order o) {
        try { return getFullOrderById(o.getId()); } catch (Exception e) { return null; }
    }

    @Override
    public java.util.List<com.cosmate.dto.response.OrderFullResponse> listOrdersByProvider(Integer providerId) throws Exception {
        List<Order> orders = orderRepository.findByProviderIdOrderByCreatedAtDesc(providerId);
        return orders.stream().map(this::mapToFullResponseSafe).filter(x -> x != null).toList();
    }

    @Override
    public java.util.List<com.cosmate.dto.response.OrderFullResponse> filterOrdersByProviderAndStatuses(Integer providerId, java.util.List<String> statuses, Integer currentUserId, boolean isAdminStaff) throws Exception {
        List<Order> orders = orderRepository.findByProviderIdAndStatusInOrderByCreatedAtDesc(providerId, statuses);
        return orders.stream().map(this::mapToFullResponseSafe).filter(x -> x != null).toList();
    }

    @Override
    public java.util.List<com.cosmate.dto.response.OrderFullResponse> listOrdersByUserId(Integer userId) throws Exception {
        List<Order> orders = orderRepository.findByCosplayerIdOrderByCreatedAtDesc(userId);
        return orders.stream().map(this::mapToFullResponseSafe).filter(x -> x != null).toList();
    }

    @Override
    @Transactional
    public java.util.Map<String,Object> shipOrder(Integer currentUserId, Integer id, String trackingCode, String shippingCarrierName, org.springframework.web.multipart.MultipartFile[] images, java.util.List<String> notes, boolean isAdminStaff) throws Exception {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) throw new IllegalArgumentException("Order not found");
        if (!isAdminStaff) {
            com.cosmate.entity.Provider p = providerService.getByUserId(currentUserId);
            if (p == null || p.getId() == null || !p.getId().equals(order.getProviderId())) throw new IllegalArgumentException("No permission");
        }
        if (!"PREPARING".equals(order.getStatus())) throw new IllegalArgumentException("Order must be in PREPARING status to ship");
        if (trackingCode == null || trackingCode.isBlank()) throw new IllegalArgumentException("trackingCode is required");
        if (images == null || images.length == 0) throw new IllegalArgumentException("At least one image file is required");

        OrderTracking ot = OrderTracking.builder()
                .order(order)
                .trackingCode(trackingCode)
                .trackingStatus("CREATED")
                .stage("SHIPPING_OUT")
                .shippingCarrierName(shippingCarrierName)
                .build();
        ot = orderTrackingRepository.save(ot);

        java.util.List<Integer> savedImageIds = new java.util.ArrayList<>();
        for (int i = 0; i < images.length; i++) {
            org.springframework.web.multipart.MultipartFile file = images[i];
            if (file == null || file.isEmpty()) continue;
            String original = file.getOriginalFilename();
            String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");
            String path = String.format("orders/%d/shipping/%d_%s", order.getId(), System.currentTimeMillis(), safeName);
            String url = firebaseStorageService.uploadFile(file, path);
            String note = (notes != null && notes.size() > i) ? notes.get(i) : null;
            java.util.List<OrderDetail> detailsForOrder = orderDetailRepository.findByOrderId(order.getId());
            OrderDetail detailForImage = detailsForOrder.isEmpty() ? null : detailsForOrder.get(0);
            OrderImage oi = OrderImage.builder()
                    .orderDetail(detailForImage)
                    .imageUrl(url)
                    .stage("SHIPPING_OUT")
                    .note(note)
                    .confirm(false)
                    .build();
            oi = orderImageRepository.save(oi);
            savedImageIds.add(oi.getId());
        }

        order.setStatus("SHIPPING_OUT");
        orderRepository.save(order);
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                    .type("ORDER_STATUS")
                    .header("Đơn hàng đã được gửi")
                    .content("Đơn hàng #" + order.getId() + " đang được gửi (SHIPPING_OUT).")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}

        java.util.Map<String,Object> result = new java.util.HashMap<>();
        result.put("tracking", ot);
        List<Integer> detailIdsForImages = orderDetailRepository.findByOrderId(order.getId()).stream().map(d -> d.getId()).toList();
        List<OrderImage> uploadedImages = detailIdsForImages.isEmpty() ? java.util.Collections.emptyList() : orderImageRepository.findByOrderDetailIdIn(detailIdsForImages);
        result.put("images", uploadedImages);
        return result;
    }

    @Override
    @Transactional
    public java.util.Map<String,Object> markDeliveringOut(Integer currentUserId, Integer id, boolean isAdminStaff) throws Exception {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) throw new IllegalArgumentException("Order not found");
        if (!isAdminStaff) {
            com.cosmate.entity.Provider p = providerService.getByUserId(currentUserId);
            if (p == null || p.getId() == null || !p.getId().equals(order.getProviderId())) throw new IllegalArgumentException("No permission");
        }
        if (!"SHIPPING_OUT".equals(order.getStatus())) throw new IllegalArgumentException("Order must be in SHIPPING_OUT status to mark delivering out");
        // Do NOT create a new OrderTracking entry here because the table is reserved for
        // user-submitted tracking codes. Instead, include the most recent existing
        // tracking record (if any) in the response so callers can see the active code.
        List<OrderTracking> existing = orderTrackingRepository.findByOrderId(order.getId());
        OrderTracking ot = null;
        if (existing != null && !existing.isEmpty()) {
            // find the last tracking that has a non-null trackingCode (user submitted)
            for (int i = existing.size() - 1; i >= 0; i--) {
                OrderTracking t = existing.get(i);
                if (t.getTrackingCode() != null && !t.getTrackingCode().isBlank()) { ot = t; break; }
            }
        }
        order.setStatus("DELIVERING_OUT");
        orderRepository.save(order);
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                    .type("ORDER_STATUS")
                    .header("Đơn hàng đang giao")
                    .content("Đơn hàng #" + order.getId() + " đang được giao (DELIVERING_OUT).")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}
        java.util.Map<String,Object> res = new java.util.HashMap<>();
        res.put("tracking", ot);
        res.put("orderStatus", order.getStatus());
        return res;
    }

    @Override
    @Transactional
    public java.util.Map<String,Object> confirmDelivery(Integer currentUserId, Integer id, org.springframework.web.multipart.MultipartFile[] images, java.util.List<String> notes) throws Exception {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) throw new IllegalArgumentException("Order not found");
        if (!currentUserId.equals(order.getCosplayerId())) throw new IllegalArgumentException("Order does not belong to user");
        if (!"DELIVERING_OUT".equals(order.getStatus())) throw new IllegalArgumentException("Order must be in DELIVERING_OUT status to confirm delivery");

        List<OrderImage> uploadedImages = new java.util.ArrayList<>();
        if (images != null && images.length > 0) {
            for (int i = 0; i < images.length; i++) {
                org.springframework.web.multipart.MultipartFile file = images[i];
                if (file == null || file.isEmpty()) continue;
                String original = file.getOriginalFilename();
                String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");
                String path = String.format("orders/%d/received/%d_%s", order.getId(), System.currentTimeMillis(), safeName);
                String url = firebaseStorageService.uploadFile(file, path);
                String note = (notes != null && notes.size() > i) ? notes.get(i) : null;
                java.util.List<OrderDetail> detailsForOrder = orderDetailRepository.findByOrderId(order.getId());
                OrderDetail detailForImage = detailsForOrder.isEmpty() ? null : detailsForOrder.get(0);
                OrderImage oi = OrderImage.builder()
                        .orderDetail(detailForImage)
                        .imageUrl(url)
                        .stage("IN_USE")
                        .note(note)
                        .confirm(false)
                        .build();
                oi = orderImageRepository.save(oi);
                uploadedImages.add(oi);
            }
        }

        // Do NOT create a new OrderTracking entry for confirmation; only read existing user-submitted tracking code
        List<OrderTracking> existing = orderTrackingRepository.findByOrderId(order.getId());
        OrderTracking ot = null;
        if (existing != null && !existing.isEmpty()) {
            for (int i = existing.size() - 1; i >= 0; i--) {
                OrderTracking t = existing.get(i);
                if (t.getTrackingCode() != null && !t.getTrackingCode().isBlank()) { ot = t; break; }
            }
        }
        order.setStatus("IN_USE");
        orderRepository.save(order);

        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                    .type("ORDER_STATUS")
                    .header("Xác nhận nhận hàng")
                    .content("Đơn hàng #" + order.getId() + " đã được xác nhận nhận và đang trong quá trình sử dụng (IN_USE).")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}

        try {
            Integer providerUserId = null;
            try {
                com.cosmate.entity.Provider prov = providerService.getById(order.getProviderId());
                if (prov != null) providerUserId = prov.getUserId();
            } catch (Exception ignored2) { providerUserId = null; }
            if (providerUserId != null) {
                com.cosmate.entity.Notification pn = com.cosmate.entity.Notification.builder()
                        .user(com.cosmate.entity.User.builder().id(providerUserId).build())
                        .type("ORDER_STATUS")
                        .header("Khách đã xác nhận nhận hàng")
                        .content("Khách hàng đã xác nhận nhận hàng cho đơn #" + order.getId() + ".")
                        .sendAt(java.time.LocalDateTime.now())
                        .isRead(false)
                        .build();
                notificationService.create(pn);
            }
        } catch (Exception ignored) {}

        List<Integer> detIdsForConfirm = orderDetailRepository.findByOrderId(order.getId()).stream().map(d -> d.getId()).toList();
        if (!detIdsForConfirm.isEmpty()) {
            List<OrderImage> imgsToCheck = orderImageRepository.findByOrderDetailIdIn(detIdsForConfirm);
            boolean changed = false;
            for (OrderImage img : imgsToCheck) {
                if (img != null && img.getStage() != null && "SHIPPING_OUT".equalsIgnoreCase(img.getStage()) && (img.getConfirm() == null || !img.getConfirm())) {
                    img.setConfirm(true);
                    changed = true;
                }
            }
            if (changed) orderImageRepository.saveAll(imgsToCheck);
        }

        java.util.Map<String,Object> res = new java.util.HashMap<>();
        res.put("tracking", ot);
        res.put("orderStatus", order.getStatus());
        res.put("uploadedImages", uploadedImages);
        return res;
    }

    @Override
    @Transactional
    public String prepareOrder(Integer currentUserId, Integer id, boolean isAdminStaff) throws Exception {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) throw new IllegalArgumentException("Order not found");
        if (!isAdminStaff) {
            com.cosmate.entity.Provider p = providerService.getByUserId(currentUserId);
            if (p == null || p.getId() == null || !p.getId().equals(order.getProviderId())) throw new IllegalArgumentException("No permission");
        }
        if (!"PAID".equals(order.getStatus())) throw new IllegalArgumentException("Order must be in PAID status to move to PREPARING");
        order.setStatus("PREPARING");
        orderRepository.save(order);
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                    .type("ORDER_STATUS")
                    .header("Đang chuẩn bị")
                    .content("Đơn hàng #" + order.getId() + " đang được chuẩn bị (PREPARING).")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}
        return "OK";
    }

    @Override
    @Transactional
    public java.util.Map<String,Object> startReturn(Integer currentUserId, Integer id, String trackingCode, String shippingCarrierName, org.springframework.web.multipart.MultipartFile[] images, java.util.List<String> notes) throws Exception {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) throw new IllegalArgumentException("Order not found");
        if (!currentUserId.equals(order.getCosplayerId())) throw new IllegalArgumentException("Order does not belong to user");
        if (!"IN_USE".equals(order.getStatus())) throw new IllegalArgumentException("Order must be in IN_USE status to start return");
        if (trackingCode == null || trackingCode.isBlank()) throw new IllegalArgumentException("trackingCode is required");
        if (images == null || images.length == 0) throw new IllegalArgumentException("At least one image file is required to start return");

        OrderTracking ot = OrderTracking.builder()
                .order(order)
                .trackingCode(trackingCode)
                .trackingStatus("RETURN_CREATED")
                .stage("SHIPPING_BACK")
                .shippingCarrierName(shippingCarrierName)
                .build();
        ot = orderTrackingRepository.save(ot);

        List<OrderImage> uploadedImages = new java.util.ArrayList<>();
        for (int i = 0; i < images.length; i++) {
            org.springframework.web.multipart.MultipartFile file = images[i];
            if (file == null || file.isEmpty()) continue;
            String original = file.getOriginalFilename();
            String safeName = original == null ? String.valueOf(System.currentTimeMillis()) : original.replaceAll("[^a-zA-Z0-9._-]", "_");
            String path = String.format("orders/%d/return/%d_%s", order.getId(), System.currentTimeMillis(), safeName);
            String url = firebaseStorageService.uploadFile(file, path);
            String note = (notes != null && notes.size() > i) ? notes.get(i) : null;
            java.util.List<OrderDetail> detailsForOrder = orderDetailRepository.findByOrderId(order.getId());
            OrderDetail detailForImage = detailsForOrder.isEmpty() ? null : detailsForOrder.get(0);
            OrderImage oi = OrderImage.builder()
                    .orderDetail(detailForImage)
                    .imageUrl(url)
                    .stage("SHIPPING_BACK")
                    .note(note)
                    .confirm(false)
                    .build();
            oi = orderImageRepository.save(oi);
            uploadedImages.add(oi);
        }

        order.setStatus("SHIPPING_BACK");
        orderRepository.save(order);
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                    .type("ORDER_STATUS")
                    .header("Đang trả hàng")
                    .content("Đơn hàng #" + order.getId() + " đang trong quá trình trả hàng (SHIPPING_BACK).")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}

        java.util.Map<String,Object> res = new java.util.HashMap<>();
        res.put("tracking", ot);
        res.put("orderStatus", order.getStatus());
        List<Integer> detIds = orderDetailRepository.findByOrderId(order.getId()).stream().map(d -> d.getId()).toList();
        res.put("uploadedImages", detIds.isEmpty() ? java.util.Collections.emptyList() : orderImageRepository.findByOrderDetailIdIn(detIds));
        return res;
    }

    @Override
    @Transactional
    public java.util.Map<String,Object> completeOrder(Integer currentUserId, Integer id, boolean isAdminStaff) throws Exception {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) throw new IllegalArgumentException("Order not found");
        if (!isAdminStaff) {
            com.cosmate.entity.Provider p = providerService.getByUserId(currentUserId);
            if (p == null || p.getId() == null || !p.getId().equals(order.getProviderId())) throw new IllegalArgumentException("No permission");
        }
        if (!"SHIPPING_BACK".equals(order.getStatus())) throw new IllegalArgumentException("Order must be in SHIPPING_BACK status to complete");

        // Do NOT create a new OrderTracking entry for return receipt; only include existing user-submitted tracking
        List<OrderTracking> existing = orderTrackingRepository.findByOrderId(order.getId());
        OrderTracking ot = null;
        if (existing != null && !existing.isEmpty()) {
            for (int i = existing.size() - 1; i >= 0; i--) {
                OrderTracking t = existing.get(i);
                if (t.getTrackingCode() != null && !t.getTrackingCode().isBlank()) { ot = t; break; }
            }
        }

        java.math.BigDecimal total = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();

        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
        java.math.BigDecimal depositTotal = java.math.BigDecimal.ZERO;
        if (details != null && !details.isEmpty()) {
            for (OrderDetail d : details) {
                try { java.math.BigDecimal dep = d.getDepositAmount() == null ? java.math.BigDecimal.ZERO : d.getDepositAmount(); depositTotal = depositTotal.add(dep); } catch (Exception ignored) {}
            }
        }
        if (depositTotal == null) depositTotal = java.math.BigDecimal.ZERO;
        java.math.BigDecimal providerShare = total.subtract(depositTotal);
        // Add any paid extension amounts for this order to provider payout
        java.math.BigDecimal extendTotalPaid = java.math.BigDecimal.ZERO;
        if (details != null && !details.isEmpty()) {
            for (OrderDetail d : details) {
                try {
                    java.util.List<com.cosmate.entity.OrderDetailExtend> exts = orderDetailExtendRepository.findByOrderDetailId(d.getId());
                    if (exts != null && !exts.isEmpty()) {
                        for (com.cosmate.entity.OrderDetailExtend ex : exts) {
                            if (ex != null && "PAID".equalsIgnoreCase(ex.getPaymentStatus())) {
                                java.math.BigDecimal p = ex.getExtendPrice() == null ? java.math.BigDecimal.ZERO : ex.getExtendPrice();
                                extendTotalPaid = extendTotalPaid.add(p);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        providerShare = providerShare.add(extendTotalPaid);
        if (providerShare == null) providerShare = java.math.BigDecimal.ZERO;
        if (providerShare.compareTo(java.math.BigDecimal.ZERO) < 0) providerShare = java.math.BigDecimal.ZERO;

        java.util.List<com.cosmate.entity.Transaction> txs = new java.util.ArrayList<>();
        if (depositTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
            com.cosmate.entity.User cosUser = com.cosmate.entity.User.builder().id(order.getCosplayerId()).build();
            com.cosmate.entity.Wallet cosWallet = walletService.createForUser(cosUser);
            com.cosmate.entity.Transaction txDeposit = walletService.credit(cosWallet, depositTotal, "Deposit returned on order completion", "DEPOSIT_RETURN:" + order.getId(), null, order);
            if (txDeposit != null) txs.add(txDeposit);
        }
        if (providerShare.compareTo(java.math.BigDecimal.ZERO) > 0) {
            // Ensure we credit the provider's USER wallet (provider has separate id and userId)
            Integer providerUserId = null;
            try {
                com.cosmate.entity.Provider provEntity = null;
                try { provEntity = providerService.getById(order.getProviderId()); } catch (Exception ignored) { provEntity = null; }
                if (provEntity != null && provEntity.getUserId() != null) {
                    providerUserId = provEntity.getUserId();
                }
            } catch (Exception ignored) {}

            if (providerUserId != null) {
                java.util.Optional<com.cosmate.entity.Wallet> wopt = walletService.getByUserId(providerUserId);
                if (wopt.isPresent()) {
                    com.cosmate.entity.Wallet provWallet = wopt.get();
                    com.cosmate.entity.Transaction txProv = walletService.credit(provWallet, providerShare, "Provider payout on order completion", "PROVIDER_PAYOUT:" + order.getId(), null, order);
                    if (txProv != null) txs.add(txProv);
                } else {
                    java.util.Optional<com.cosmate.entity.User> providerUserOpt = userRepository.findById(providerUserId);
                    if (providerUserOpt.isPresent()) {
                        walletService.createForUser(providerUserOpt.get());
                        java.util.Optional<com.cosmate.entity.Wallet> wopt2 = walletService.getByUserId(providerUserId);
                        if (wopt2.isPresent()) {
                            com.cosmate.entity.Wallet provWallet = wopt2.get();
                            com.cosmate.entity.Transaction txProv = walletService.credit(provWallet, providerShare, "Provider payout on order completion", "PROVIDER_PAYOUT:" + order.getId(), null, order);
                            if (txProv != null) txs.add(txProv);
                        }
                    }
                }
            } else {
                // If we can't find a provider user to credit, log an error and skip the payout (but still complete the order)
                System.err.println("Unable to credit provider payout for order " + order.getId() + " because provider user was not found");
            }
        }

        order.setStatus("COMPLETED");
        orderRepository.save(order);
        try {
            com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                    .user(com.cosmate.entity.User.builder().id(order.getCosplayerId()).build())
                    .type("ORDER_STATUS")
                    .header("Đơn hàng hoàn tất")
                    .content("Đơn hàng #" + order.getId() + " đã hoàn tất (COMPLETED).")
                    .sendAt(java.time.LocalDateTime.now())
                    .isRead(false)
                    .build();
            notificationService.create(n);
        } catch (Exception ignored) {}

        try {
            if (details != null && !details.isEmpty()) {
                for (OrderDetail d : details) {
                    if (d.getCostumeId() == null) continue;
                    Costume c = costumeRepository.findById(d.getCostumeId()).orElse(null);
                    if (c != null) {
                        // update completed rent count: if null or 0 -> set to 1, otherwise increment
                        Integer crc = c.getCompletedRentCount();
                        if (crc == null || crc == 0) c.setCompletedRentCount(1);
                        else c.setCompletedRentCount(crc + 1);
                        c.setStatus("AVAILABLE");
                        costumeRepository.save(c);
                        try {
                            java.util.List<com.cosmate.entity.WishlistCostume> watchers = wishlistRepository.findAllByCostumeId(c.getId());
                            if (watchers != null && !watchers.isEmpty()) {
                                for (com.cosmate.entity.WishlistCostume w : watchers) {
                                    try {
                                        com.cosmate.entity.Notification n = com.cosmate.entity.Notification.builder()
                                                .user(com.cosmate.entity.User.builder().id(w.getUserId()).build())
                                                .type("WISHLIST_NOTIFY")
                                                .header("Bộ đồ bạn quan tâm đã có sẵn")
                                                .content("Bộ đồ '" + c.getName() + "' hiện đã có sẵn để thuê.")
                                                .sendAt(java.time.LocalDateTime.now())
                                                .isRead(false)
                                                .build();
                                        notificationService.create(n);
                                    } catch (Exception ignored) {}
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}

        // increment provider completed orders count by 1
        try {
            if (order.getProviderId() != null) {
                try { providerService.incrementCompletedOrders(order.getProviderId()); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        java.util.Map<String,Object> res = new java.util.HashMap<>();
        res.put("tracking", ot);
        res.put("orderStatus", order.getStatus());
        res.put("transactions", txs);
        res.put("depositReturned", depositTotal);
        res.put("providerPayout", providerShare);
        return res;
    }

    @Override
    public java.util.List<com.cosmate.dto.response.TransactionResponse> getTransactionsForOrder(Integer id) throws Exception {
        List<com.cosmate.entity.Transaction> txs = transactionRepository.findByOrder_IdOrderByCreatedAtDesc(id);
        return txs.stream().map(t -> com.cosmate.dto.response.TransactionResponse.builder()
                .id(t.getId())
                .amount(t.getAmount())
                .type(t.getType())
                .status(t.getStatus())
                .paymentMethod(t.getPaymentMethod())
                .walletId(t.getWallet() == null ? null : t.getWallet().getWalletId())
                .orderId(t.getOrder() == null ? null : t.getOrder().getId())
                .createdAt(t.getCreatedAt())
                .build()).toList();
    }
}
