package com.petshop.config;

import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Initializes rich realistic sample data for customer accounts
 * (customer@petshop.vn, giabaonek2004@gmail.com, etc.)
 * for Profile, My Orders, Address Book, and Cart.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileDemoDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final UserAddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final VoucherRepository voucherRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Override
    public void run(String... args) {
        List<String> demoEmails = List.of("customer@petshop.vn", "giabaonek2004@gmail.com");
        for (String email : demoEmails) {
            try {
                Optional<User> customerOpt = userRepository.findByEmail(email);
                User customer = null;
                if (customerOpt.isPresent()) {
                    customer = customerOpt.get();
                } else if ("giabaonek2004@gmail.com".equalsIgnoreCase(email)) {
                    Role customerRole = roleRepository.findByCode("CUSTOMER").orElse(null);
                    if (customerRole != null) {
                        User newUser = User.builder()
                                .email(email)
                                .fullName("Gia Bảo")
                                .phone("0904123456")
                                .role(customerRole)
                                .passwordHash(passwordEncoder.encode("Customer@123"))
                                .status("ACTIVE")
                                .build();
                        customer = userRepository.save(newUser);
                        log.debug("Created user account for demo: {}", email);
                    }
                }

                if (customer != null) {
                    seedForUser(customer);
                    log.debug("Profile demo data verified/initialized for: {}", email);
                }
            } catch (Exception e) {
                log.debug("Could not complete profile demo data initialization for {}: {}", email, e.getMessage());
            }
        }
    }

    public void seedForUser(User customer) {
        if (customer == null) {
            return;
        }
        seedAddresses(customer);
        seedCart(customer);
        seedOrders(customer);
    }

    private void seedAddresses(User customer) {
        try {
            long addressCount = addressRepository.countByCustomerIdAndIsDeletedFalse(customer.getId());
            if (addressCount <= 1) {
                String name = customer.getFullName() != null && !customer.getFullName().isBlank() 
                        ? customer.getFullName() : "Gia Bảo";
                String phone = customer.getPhone() != null && !customer.getPhone().isBlank()
                        ? customer.getPhone() : "0904123456";

                if (addressCount == 0) {
                    UserAddress home = UserAddress.builder()
                            .customer(customer)
                            .recipientName(name + " (Nhà riêng)")
                            .recipientPhone(phone)
                            .province("TP. Hồ Chí Minh")
                            .district("Thủ Đức")
                            .ward("Linh Trung")
                            .addressLine("Khu phố 6, Đại học FPT")
                            .isDefault(true)
                            .isDeleted(false)
                            .build();
                    addressRepository.save(home);
                }

                UserAddress office = UserAddress.builder()
                        .customer(customer)
                        .recipientName(name + " (Văn phòng FPT)")
                        .recipientPhone(phone)
                        .province("TP. Hà Nội")
                        .district("Cầu Giấy")
                        .ward("Dịch Vọng Hậu")
                        .addressLine("Tòa nhà FPT Tower, Tầng 8, Phố Duy Tân")
                        .isDefault(false)
                        .isDeleted(false)
                        .build();
                addressRepository.save(office);

                UserAddress parents = UserAddress.builder()
                        .customer(customer)
                        .recipientName(name + " (Nhà phụ huynh)")
                        .recipientPhone("0901234888")
                        .province("TP. Hồ Chí Minh")
                        .district("Quận 1")
                        .ward("Bến Nghé")
                        .addressLine("Số 128 Nguyễn Huệ")
                        .isDefault(false)
                        .isDeleted(false)
                        .build();
                addressRepository.save(parents);
                log.info("Seeded extra sample addresses for customer ID {}", customer.getId());
            }
        } catch (Exception e) {
            log.warn("Could not seed extra addresses for user {}: {}", customer.getId(), e.getMessage());
        }
    }

    private void seedCart(User customer) {
        try {
            Cart cart = cartRepository.findByUserId(customer.getId()).orElseGet(() -> {
                Cart newCart = Cart.builder().user(customer).build();
                return cartRepository.save(newCart);
            });

            if (cartItemRepository.findByCartId(cart.getId()).isEmpty()) {
                addCartItemIfVariantExists(cart, "WHK-MACKEREL-85G", 3);
                addCartItemIfVariantExists(cart, "RC-POODLE-500G", 1);
                addCartItemIfVariantExists(cart, "BALL-RUBBER-M", 1);
                log.info("Seeded sample cart items for customer ID {}", customer.getId());
            }
        } catch (Exception e) {
            log.warn("Could not seed cart items for user {}: {}", customer.getId(), e.getMessage());
        }
    }

    private void addCartItemIfVariantExists(Cart cart, String sku, int qty) {
        variantRepository.findBySkuCode(sku).ifPresent(variant -> {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(qty)
                    .isSelected(true)
                    .build();
            cartItemRepository.save(item);
        });
    }

    @org.springframework.transaction.annotation.Transactional
    public void seedOrders(User customer) {
        try {
            long orderCount = orderRepository.countByCustomerId(customer.getId());
            if (orderCount >= 4) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            String name = customer.getFullName() != null && !customer.getFullName().isBlank() 
                    ? customer.getFullName() : "Gia Bảo";
            String phone = customer.getPhone() != null && !customer.getPhone().isBlank()
                    ? customer.getPhone() : "0904123456";
            String orderPrefix = "ORD-" + customer.getId() + "-";

            // 1. Order COMPLETED (Giao thành công)
            variantRepository.findBySkuCodeWithProduct("RC-POODLE-1500G").ifPresent(v1 -> {
                Optional<Voucher> vOpt = voucherRepository.findByCode("FREESHIP30K");
                Voucher voucher = vOpt.orElse(null);
                BigDecimal discount = (voucher != null) ? new BigDecimal("30000") : BigDecimal.ZERO;
                String voucherCode = (voucher != null) ? "FREESHIP30K" : null;
                BigDecimal total = new BigDecimal("420000").subtract(discount).add(new BigDecimal("30000"));

                String code1 = orderPrefix + "20261005-001";
                if (orderRepository.findByOrderCode(code1).isEmpty()) {
                    Order o1 = Order.builder()
                            .orderCode(code1)
                            .customer(customer)
                            .voucher(voucher)
                            .voucherCodeSnapshot(voucherCode)
                            .recipientName(name)
                            .recipientPhone(phone)
                            .shippingProvince("TP. Hồ Chí Minh")
                            .shippingDistrict("Thủ Đức")
                            .shippingWard("Linh Trung")
                            .shippingAddressLine("Khu phố 6, Đại học FPT")
                            .customerNote("Giao giờ hành chính, gọi trước khi đến")
                            .subtotalAmount(new BigDecimal("420000"))
                            .discountAmount(discount)
                            .shippingFee(new BigDecimal("30000"))
                            .totalAmount(total)
                            .orderStatus(OrderStatus.COMPLETED)
                            .paymentMethod(PaymentMethod.COD)
                            .paymentStatus(PaymentStatus.PAID)
                            .completedAt(now.minusDays(5))
                            .build();

                    OrderItem item1 = OrderItem.builder()
                            .order(o1)
                            .variant(v1)
                            .productNameSnapshot(v1.getProduct().getName())
                            .skuCodeSnapshot(v1.getSkuCode())
                            .variantNameSnapshot(v1.getVariantName())
                            .unitPrice(v1.getPrice())
                            .quantity(1)
                            .build();
                    o1.getItems().add(item1);

                    Order saved1 = orderRepository.save(o1);

                    saveStatusHistory(saved1, null, OrderStatus.PENDING_CONFIRMATION, "Khách hàng đặt đơn thành công", now.minusDays(7));
                    saveStatusHistory(saved1, OrderStatus.PENDING_CONFIRMATION, OrderStatus.CONFIRMED, "Nhân viên xác nhận đơn", now.minusDays(7).plusHours(2));
                    saveStatusHistory(saved1, OrderStatus.CONFIRMED, OrderStatus.SHIPPING, "Đã giao cho đơn vị vận chuyển", now.minusDays(6));
                    saveStatusHistory(saved1, OrderStatus.SHIPPING, OrderStatus.COMPLETED, "Giao hàng thành công & thu tiền COD", now.minusDays(5));

                    PaymentTransaction tx1 = PaymentTransaction.builder()
                            .order(saved1)
                            .customer(customer)
                            .transactionCode("TXN-COD-" + customer.getId() + "-001")
                            .paymentMethod(PaymentMethod.COD)
                            .paymentEnvironment(PaymentEnvironment.INTERNAL)
                            .amount(saved1.getTotalAmount())
                            .transactionStatus(TransactionStatus.SUCCESS)
                            .responseCode("00")
                            .paidAt(now.minusDays(5))
                            .build();
                    paymentTransactionRepository.save(tx1);
                }
            });

            // 2. Order SHIPPING (Đang vận chuyển)
            Optional<ProductVariant> v2a = variantRepository.findBySkuCodeWithProduct("MEO-LITTER-8L");
            Optional<ProductVariant> v2b = variantRepository.findBySkuCodeWithProduct("LEASH-SET-M");
            if (v2a.isPresent() && v2b.isPresent()) {
                String code2 = orderPrefix + "20261011-002";
                if (orderRepository.findByOrderCode(code2).isEmpty()) {
                    Order o2 = Order.builder()
                            .orderCode(code2)
                            .customer(customer)
                            .voucher(null)
                            .voucherCodeSnapshot(null)
                            .recipientName(name + " (Văn phòng)")
                            .recipientPhone(phone)
                            .shippingProvince("TP. Hà Nội")
                            .shippingDistrict("Cầu Giấy")
                            .shippingWard("Dịch Vọng Hậu")
                            .shippingAddressLine("Tòa nhà FPT Tower, Tầng 8, Phố Duy Tân")
                            .customerNote("Giao sảnh lễ tân tầng 1")
                            .subtotalAmount(new BigDecimal("289000"))
                            .discountAmount(BigDecimal.ZERO)
                            .shippingFee(new BigDecimal("30000"))
                            .totalAmount(new BigDecimal("319000"))
                            .orderStatus(OrderStatus.SHIPPING)
                            .paymentMethod(PaymentMethod.VNPAY)
                            .paymentStatus(PaymentStatus.PAID)
                            .build();

                    OrderItem item2a = OrderItem.builder()
                            .order(o2)
                            .variant(v2a.get())
                            .productNameSnapshot(v2a.get().getProduct().getName())
                            .skuCodeSnapshot(v2a.get().getSkuCode())
                            .variantNameSnapshot(v2a.get().getVariantName())
                            .unitPrice(new BigDecimal("95000"))
                            .quantity(2)
                            .build();
                    o2.getItems().add(item2a);

                    OrderItem item2b = OrderItem.builder()
                            .order(o2)
                            .variant(v2b.get())
                            .productNameSnapshot(v2b.get().getProduct().getName())
                            .skuCodeSnapshot(v2b.get().getSkuCode())
                            .variantNameSnapshot(v2b.get().getVariantName())
                            .unitPrice(new BigDecimal("99000"))
                            .quantity(1)
                            .build();
                    o2.getItems().add(item2b);

                    Order saved2 = orderRepository.save(o2);

                    saveStatusHistory(saved2, null, OrderStatus.PENDING_PAYMENT, "Tạo đơn hàng thanh toán VNPay", now.minusDays(1));
                    saveStatusHistory(saved2, OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED, "VNPay xác nhận thanh toán thành công", now.minusDays(1).plusMinutes(3));
                    saveStatusHistory(saved2, OrderStatus.CONFIRMED, OrderStatus.SHIPPING, "Đang trên đường giao tới khách", now.minusHours(8));

                    PaymentTransaction tx2 = PaymentTransaction.builder()
                            .order(saved2)
                            .customer(customer)
                            .transactionCode("VNPAY-" + customer.getId() + "-9988")
                            .paymentMethod(PaymentMethod.VNPAY)
                            .amount(saved2.getTotalAmount())
                            .transactionStatus(TransactionStatus.SUCCESS)
                            .responseCode("00")
                            .paidAt(now.minusDays(1).plusMinutes(3))
                            .build();
                    paymentTransactionRepository.save(tx2);
                }
            }

            // 3. Order CONFIRMED (Đã xác nhận / Chuẩn bị)
            Optional<ProductVariant> v3a = variantRepository.findBySkuCodeWithProduct("BALL-RUBBER-S");
            Optional<ProductVariant> v3b = variantRepository.findBySkuCodeWithProduct("RC-POODLE-500G");
            if (v3a.isPresent() && v3b.isPresent()) {
                String code3 = orderPrefix + "20261012-003";
                if (orderRepository.findByOrderCode(code3).isEmpty()) {
                    Order o3 = Order.builder()
                            .orderCode(code3)
                            .customer(customer)
                            .voucher(null)
                            .voucherCodeSnapshot(null)
                            .recipientName(name)
                            .recipientPhone(phone)
                            .shippingProvince("TP. Hồ Chí Minh")
                            .shippingDistrict("Thủ Đức")
                            .shippingWard("Linh Trung")
                            .shippingAddressLine("Khu phố 6, Đại học FPT")
                            .customerNote("Đóng gói cẩn thận giúp mình nhé")
                            .subtotalAmount(new BigDecimal("200000"))
                            .discountAmount(BigDecimal.ZERO)
                            .shippingFee(new BigDecimal("30000"))
                            .totalAmount(new BigDecimal("230000"))
                            .orderStatus(OrderStatus.CONFIRMED)
                            .paymentMethod(PaymentMethod.COD)
                            .paymentStatus(PaymentStatus.UNPAID)
                            .build();

                    OrderItem item3a = OrderItem.builder()
                            .order(o3)
                            .variant(v3a.get())
                            .productNameSnapshot(v3a.get().getProduct().getName())
                            .skuCodeSnapshot(v3a.get().getSkuCode())
                            .variantNameSnapshot(v3a.get().getVariantName())
                            .unitPrice(new BigDecimal("35000"))
                            .quantity(1)
                            .build();
                    o3.getItems().add(item3a);

                    OrderItem item3b = OrderItem.builder()
                            .order(o3)
                            .variant(v3b.get())
                            .productNameSnapshot(v3b.get().getProduct().getName())
                            .skuCodeSnapshot(v3b.get().getSkuCode())
                            .variantNameSnapshot(v3b.get().getVariantName())
                            .unitPrice(new BigDecimal("165000"))
                            .quantity(1)
                            .build();
                    o3.getItems().add(item3b);

                    Order saved3 = orderRepository.save(o3);

                    saveStatusHistory(saved3, null, OrderStatus.PENDING_CONFIRMATION, "Khách đặt đơn COD", now.minusHours(4));
                    saveStatusHistory(saved3, OrderStatus.PENDING_CONFIRMATION, OrderStatus.CONFIRMED, "Kho xác nhận và tiến hành đóng gói", now.minusHours(2));
                }
            }

            // 4. Order CANCELLED (Đã hủy)
            variantRepository.findBySkuCodeWithProduct("LEASH-SET-S").ifPresent(v4 -> {
                String code4 = orderPrefix + "20261002-004";
                if (orderRepository.findByOrderCode(code4).isEmpty()) {
                    Order o4 = Order.builder()
                            .orderCode(code4)
                            .customer(customer)
                            .voucher(null)
                            .voucherCodeSnapshot(null)
                            .recipientName(name)
                            .recipientPhone(phone)
                            .shippingProvince("TP. Hồ Chí Minh")
                            .shippingDistrict("Thủ Đức")
                            .shippingWard("Linh Trung")
                            .shippingAddressLine("Khu phố 6, Đại học FPT")
                            .subtotalAmount(new BigDecimal("85000"))
                            .discountAmount(BigDecimal.ZERO)
                            .shippingFee(new BigDecimal("30000"))
                            .totalAmount(new BigDecimal("115000"))
                            .orderStatus(OrderStatus.CANCELLED)
                            .paymentMethod(PaymentMethod.COD)
                            .paymentStatus(PaymentStatus.CANCELLED)
                            .cancelType(CancelType.CUSTOMER_CANCEL)
                            .cancelReason("Khách hàng thay đổi ý định chọn size khác")
                            .cancelledAt(now.minusDays(10))
                            .build();

                    OrderItem item4 = OrderItem.builder()
                            .order(o4)
                            .variant(v4)
                            .productNameSnapshot(v4.getProduct().getName())
                            .skuCodeSnapshot(v4.getSkuCode())
                            .variantNameSnapshot(v4.getVariantName())
                            .unitPrice(new BigDecimal("85000"))
                            .quantity(1)
                            .build();
                    o4.getItems().add(item4);

                    Order saved4 = orderRepository.save(o4);

                    saveStatusHistory(saved4, null, OrderStatus.PENDING_CONFIRMATION, "Đặt hàng", now.minusDays(10));
                    saveStatusHistory(saved4, OrderStatus.PENDING_CONFIRMATION, OrderStatus.CANCELLED, "Khách hàng tự hủy đơn", now.minusDays(10));
                }
            });

            log.debug("Seeded 4 realistic sample orders for customer ID {}", customer.getId());
        } catch (Exception e) {
            log.error("Could not seed sample orders for user {}: {}", customer.getId(), e.getMessage(), e);
        }
    }

    private void saveStatusHistory(Order order, OrderStatus oldStatus, OrderStatus newStatus, String reason, LocalDateTime at) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(order.getCustomer())
                .reason(reason)
                .changedAt(at)
                .build();
        statusHistoryRepository.save(history);
    }
}
