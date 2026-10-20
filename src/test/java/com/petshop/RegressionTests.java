package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.auth.*;
import com.petshop.dto.catalog.ProductFilterDto;
import com.petshop.dto.order.CheckoutSubmitDto;
import com.petshop.dto.voucher.VoucherRequestDto;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegressionTests {
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired VoucherRepository vouchers;
    @Autowired CustomerVoucherRepository wallet;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired ReviewRepository reviews;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired UserService userService;
    @Autowired VoucherService voucherService;
    @Autowired OrderService orderService;
    @Autowired CartService carts;
    @Autowired CatalogService catalog;
    @Autowired StockImportService imports;
    @Autowired ReportService reports;
    @Autowired AuthService auth;
    @Autowired Validator validator;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    User customer,admin,staff;
    Product product;
    ProductVariant variant;
    Category category;

    @BeforeEach void fixture() {
        var cr=roles.save(Role.builder().code("CUSTOMER").name("Customer").build());
        var ar=roles.save(Role.builder().code("ADMIN").name("Admin").build());
        var sr=roles.save(Role.builder().code("STAFF").name("Staff").build());
        String hash=encoder.encode("Secret@123");
        customer=users.save(User.builder().role(cr).fullName("Customer").email("c@regression.test").phone("0901000001").passwordHash(hash).build());
        admin=users.save(User.builder().role(ar).fullName("Admin").email("a@regression.test").phone("0901000002").passwordHash(hash).build());
        staff=users.save(User.builder().role(sr).fullName("Staff").email("s@regression.test").phone("0901000003").passwordHash(hash).build());
        category=categories.save(Category.builder().code("FOOD").name("Food").build());
        product=products.save(Product.builder().category(category).createdBy(admin).name("Dog food").slug("regression-dog-food").petType(PetType.DOG).status(ProductStatus.ACTIVE).build());
        variant=variants.save(ProductVariant.builder().product(product).skuCode("REG-FOOD").variantName("Small").price(new BigDecimal("200000")).stockQuantity(10).status(ProductVariantStatus.ACTIVE).build());
        em.flush();
    }
    VoucherRequestDto voucherForm() {
        return VoucherRequestDto.builder().code("REG10").title("Regression voucher").discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.TEN).minOrderAmount(BigDecimal.ZERO).maxDiscountAmount(new BigDecimal("50000"))
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(2)).totalUsageLimit(10).perCustomerLimit(2).build();
    }
    CheckoutSubmitDto checkout() {
        return CheckoutSubmitDto.builder().recipientName("Customer").recipientPhone("0901000001")
                .shippingProvince("HCM").shippingDistrict("Thu Duc").shippingWard("Linh Trung").shippingAddressLine("123 Road")
                .paymentMethod(PaymentMethod.COD).build();
    }
    @Test void staffPasswordConfirmationRejectedBeforePersisting() {
        long count=users.count();
        var form=RegisterRequestDto.builder().fullName("New staff").email("new@regression.test").phone("0901000004").password("Secret@123").confirmPassword("Different").build();
        assertThrows(IllegalArgumentException.class,()->userService.createStaffAccount(form));
        assertEquals(count,users.count());
        form.setConfirmPassword("Secret@123");assertEquals("STAFF",userService.createStaffAccount(form).getRole().getCode());
    }
    @Test void voucherDatesMustBeStrictlyIncreasing() {
        var form=voucherForm();form.setEndDate(form.getStartDate());
        assertThrows(IllegalArgumentException.class,()->voucherService.createVoucher(form));
        assertEquals(0,vouchers.count());
    }
    @Test void zeroDiscountCapRejectedAtDtoAndService() {
        var form=voucherForm();form.setMaxDiscountAmount(BigDecimal.ZERO);
        assertFalse(validator.validate(form).isEmpty());
        assertThrows(IllegalArgumentException.class,()->voucherService.createVoucher(form));
        form.setMaxDiscountAmount(null);assertTrue(validator.validate(form).isEmpty());
        assertNotNull(voucherService.createVoucher(form).getId());
    }
    @Test void voucherQuotaCannotBeReducedBelowUsedCount() {
        Voucher v=voucherService.createVoucher(voucherForm());v.setUsedCount(5);vouchers.saveAndFlush(v);
        var form=voucherForm();form.setTotalUsageLimit(3);
        assertThrows(IllegalArgumentException.class,()->voucherService.updateVoucher(v.getId(),form));
        em.clear();assertEquals(10,vouchers.findById(v.getId()).orElseThrow().getTotalUsageLimit());
    }
    @Test void activeLastAdminCannotBeLocked() {
        assertThrows(IllegalArgumentException.class,()->userService.toggleUserStatus(admin.getId(),staff.getId()));
        assertEquals("ACTIVE",users.findById(admin.getId()).orElseThrow().getStatus());
    }
    @Test void existingSessionIsRevokedWhenAccountLockedOrRoleChanged() throws Exception {
        var old=new CustomUserDetails(customer);customer.setStatus("LOCKED");users.saveAndFlush(customer);
        mvc.perform(get("/account/profile").with(user(old))).andExpect(redirectedUrl("/login?expired=true"));
        customer.setStatus("ACTIVE");customer.setRole(staff.getRole());users.saveAndFlush(customer);
        mvc.perform(get("/account/profile").with(user(old))).andExpect(redirectedUrl("/login?expired=true"));
    }
    @Test void resetPasswordRevokesSessionAndForcesPasswordChange() throws Exception {
        var old=new CustomUserDetails(customer);String temp=userService.resetUserPassword(customer.getId(),admin.getId());
        assertTrue(temp.length()>16);assertTrue(users.findById(customer.getId()).orElseThrow().getMustChangePassword());
        mvc.perform(get("/account/profile").with(user(old))).andExpect(redirectedUrl("/login?expired=true"));
        var fresh=new CustomUserDetails(users.findById(customer.getId()).orElseThrow());
        mvc.perform(get("/account/profile").with(user(fresh))).andExpect(redirectedUrl("/account/password"));
        mvc.perform(get("/account/password").with(user(fresh))).andExpect(status().isOk());
        mvc.perform(post("/account/password").with(user(fresh)).with(csrf()).param("currentPassword",temp).param("newPassword","Changed@123").param("confirmNewPassword","Changed@123"))
                .andExpect(redirectedUrl("/login?passwordChanged=true"));
        assertFalse(users.findById(customer.getId()).orElseThrow().getMustChangePassword());
    }
    @Test void checkoutAndCancelRestoreStockAndVoucherExactlyOnce() {
        Voucher v=voucherService.createVoucher(voucherForm());voucherService.saveVoucherToWallet(customer.getId(),v.getId());
        carts.addItemToCart(customer.getId(),variant.getId(),2);
        var form=checkout();form.setVoucherCode(v.getCode());
        Order order=orderService.checkoutOrder(customer.getId(),form);
        em.flush();em.clear();
        assertEquals(8,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(1,vouchers.findById(v.getId()).orElseThrow().getUsedCount());
        assertEquals(0,new BigDecimal("390000").compareTo(order.getTotalAmount()));
        orderService.customerCancelOrder(order.getId(),customer.getId(),"Changed mind");
        em.flush();em.clear();
        assertEquals(10,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(0,vouchers.findById(v.getId()).orElseThrow().getUsedCount());
        assertThrows(IllegalStateException.class,()->orderService.customerCancelOrder(order.getId(),customer.getId(),"Again"));
        assertEquals(10,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
    }
    @Test void walletUsesOrderCountWhenVoucherAllowsMultipleUses() {
        Voucher v=voucherService.createVoucher(voucherForm());voucherService.saveVoucherToWallet(customer.getId(),v.getId());
        carts.addItemToCart(customer.getId(),variant.getId(),1);var form=checkout();form.setVoucherCode(v.getCode());
        Order first=orderService.checkoutOrder(customer.getId(),form);
        em.flush();em.clear();
        assertEquals(1,voucherService.getCustomerWalletVouchers(customer.getId()).get(0).getRemainingUses());
        carts.addItemToCart(customer.getId(),variant.getId(),1);Order second=orderService.checkoutOrder(customer.getId(),form);
        em.flush();em.clear();assertEquals(0,voucherService.getCustomerWalletVouchers(customer.getId()).get(0).getRemainingUses());
        orderService.customerCancelOrder(first.getId(),customer.getId(),"Cancel one");em.flush();em.clear();
        assertEquals(1,voucherService.getCustomerWalletVouchers(customer.getId()).get(0).getRemainingUses());
        assertTrue(wallet.findByCustomerIdAndVoucherId(customer.getId(),v.getId()).orElseThrow().getIsUsed());
        orderService.customerCancelOrder(second.getId(),customer.getId(),"Cancel second");em.flush();em.clear();
        assertFalse(wallet.findByCustomerIdAndVoucherId(customer.getId(),v.getId()).orElseThrow().getIsUsed());
    }
    @Test void onlySelectedCartItemsContributeToCheckout() {
        var cart=carts.addItemToCart(customer.getId(),variant.getId(),1);
        carts.selectItem(customer.getId(),cart.getItems().get(0).getId(),false);
        assertEquals(0,BigDecimal.ZERO.compareTo(carts.getCartForUser(customer.getId()).getSubtotal()));
        assertThrows(IllegalArgumentException.class,()->orderService.checkoutOrder(customer.getId(),checkout()));
        carts.selectItem(customer.getId(),cart.getItems().get(0).getId(),true);
        assertEquals(0,new BigDecimal("200000").compareTo(carts.getCartForUser(customer.getId()).getSubtotal()));
    }
    @Test void hiddenProductCannotBeBoughtOrViewed() {
        product.setStatus(ProductStatus.HIDDEN);products.saveAndFlush(product);
        assertThrows(IllegalArgumentException.class,()->carts.addItemToCart(customer.getId(),variant.getId(),1));
        assertThrows(IllegalArgumentException.class,()->catalog.getProductBySlug(product.getSlug()));
    }
    @Test void combinedCatalogFiltersAndPriceSortingWork() {
        var filter=ProductFilterDto.builder().keyword("Dog").categoryCode("FOOD").petType(PetType.DOG).minPrice(new BigDecimal("190000")).maxPrice(new BigDecimal("210000")).sortBy("price_desc").build();
        assertEquals(1,catalog.searchAndFilterProducts(filter,PageRequest.of(0,10)).getTotalElements());
        filter.setPetType(PetType.CAT);assertEquals(0,catalog.searchAndFilterProducts(filter,PageRequest.of(0,10)).getTotalElements());
        filter.setPetType(PetType.DOG);filter.setMinPrice(new BigDecimal("250000"));assertEquals(0,catalog.searchAndFilterProducts(filter,PageRequest.of(0,10)).getTotalElements());
    }
    @Test void unavailableVnpayCannotCreateFalseSuccessOrder() {
        carts.addItemToCart(customer.getId(),variant.getId(),1);var form=checkout();form.setPaymentMethod(PaymentMethod.VNPAY);
        assertThrows(IllegalArgumentException.class,()->orderService.checkoutOrder(customer.getId(),form));
        assertEquals(0,orders.count());assertEquals(10,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
    }
    @Test void stockImportRequiresItemsAndProductVariantStartsAtZero() {
        assertThrows(IllegalArgumentException.class,()->imports.createStockImport(new StockImport(),List.of(),staff.getId()));
        var pv=ProductVariant.builder().skuCode("REG-NEW").variantName("New").price(BigDecimal.TEN).stockQuantity(500).build();
        assertEquals(0,manage.addVariant(product.getId(),pv).getStockQuantity());
    }
    @Autowired ProductManageService manage;
    @Test void reportsRenderWithNoCompletedOrdersAndNoReviews() throws Exception {
        for (String path : List.of("/admin/reports/revenue?groupBy=month","/admin/reports/revenue?groupBy=year","/admin/reports/stock","/admin/reports/reviews"))
            mvc.perform(get(path).with(user(new CustomUserDetails(admin)))).andExpect(status().isOk());
        assertEquals(1,reports.stockReconciliation().size());
        assertEquals(0.0,reviews.getOverallAverageRating());
    }
    @Test void staffCanOpenOwnProfileAndCustomerStatusFilterWorks() throws Exception {
        mvc.perform(get("/account/profile").with(user(new CustomUserDetails(staff)))).andExpect(status().isOk());
        assertEquals(1,userService.getAllUsers(null,"CUSTOMER","ACTIVE",PageRequest.of(0,10)).getTotalElements());
        assertEquals(0,userService.getAllUsers(null,"CUSTOMER","LOCKED",PageRequest.of(0,10)).getTotalElements());
    }

    @Test void completedRevenueExcludesShippingAndReviewRedirectKeepsKeyword() throws Exception {
        carts.addItemToCart(customer.getId(),variant.getId(),2);
        var order=orderService.checkoutOrder(customer.getId(),checkout());
        orderService.confirmOrder(order.getId(),staff.getId());
        orderService.markPreparing(order.getId(),staff.getId());
        orderService.markShipping(order.getId(),staff.getId());
        orderService.markCompleted(order.getId(),staff.getId());
        em.flush();
        var view=mvc.perform(get("/admin/reports/revenue?groupBy=month").with(user(new CustomUserDetails(admin)))).andExpect(status().isOk()).andReturn().getModelAndView();
        assertEquals(0,new BigDecimal("400000").compareTo((BigDecimal)view.getModel().get("totalRevenue")));
        assertEquals(0,new BigDecimal("430000").compareTo((BigDecimal)view.getModel().get("totalCollected")));
        assertEquals(1,reports.topSelling(LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1)).size());
        var item=orderItems.findByOrderId(order.getId()).get(0);
        var review=reviews.saveAndFlush(Review.builder().orderItem(item).customer(customer).rating(4).comment("Good").status(ReviewStatus.VISIBLE).build());
        var response=mvc.perform(post("/admin/reviews/"+review.getId()+"/toggle-status").with(user(new CustomUserDetails(admin))).with(csrf()).param("keyword","a&status=HIDDEN")).andReturn().getResponse();
        assertTrue(response.getRedirectedUrl().contains("keyword=a%26status%3DHIDDEN"));
        assertEquals(1,reports.reviewsByProduct(LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1)).size());
    }

    @Test void passwordResetTokenCanOnlyBeUsedOnceAndRejectsWeakPassword() throws Exception {
        String raw="regression-token";
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var token=tokens.saveAndFlush(PasswordResetToken.builder().user(customer).tokenHash(hash).expiresAt(LocalDateTime.now().plusHours(1)).build());
        assertThrows(IllegalArgumentException.class,()->auth.resetPasswordWithToken(raw,"123"));
        assertNull(tokens.findById(token.getId()).orElseThrow().getUsedAt());
        auth.resetPasswordWithToken(raw,"Reset@123");
        assertTrue(encoder.matches("Reset@123",users.findById(customer.getId()).orElseThrow().getPasswordHash()));
        assertThrows(IllegalArgumentException.class,()->auth.resetPasswordWithToken(raw,"Another@123"));
    }

    @Test void softDeletePreservesUsedVoucherOrderAndCancellation() throws Exception {
        Voucher voucher = voucherService.createVoucher(voucherForm());
        voucherService.saveVoucherToWallet(customer.getId(), voucher.getId());
        carts.addItemToCart(customer.getId(), variant.getId(), 2);
        var form = checkout(); form.setVoucherCode(voucher.getCode());
        Order order = orderService.checkoutOrder(customer.getId(), form);
        mvc.perform(post("/admin/vouchers/" + voucher.getId() + "/delete")
                .with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(redirectedUrl("/admin/vouchers"));
        em.flush(); em.clear();
        Voucher deleted = vouchers.findById(voucher.getId()).orElseThrow();
        assertTrue(deleted.getIsDeleted());
        assertNotNull(deleted.getDeletedAt());
        assertEquals(VoucherStatus.INACTIVE, deleted.getStatus());
        assertEquals(1, deleted.getUsedCount());
        Order persisted = orders.findById(order.getId()).orElseThrow();
        assertEquals(voucher.getId(), persisted.getVoucher().getId());
        assertEquals(voucher.getCode(), persisted.getVoucherCodeSnapshot());
        assertEquals(0, new BigDecimal("390000").compareTo(persisted.getTotalAmount()));
        mvc.perform(get("/customer/orders/" + order.getId()).with(user(new CustomUserDetails(customer))))
                .andExpect(status().isOk());
        assertTrue(voucherService.getCustomerWalletVouchers(customer.getId()).isEmpty());
        assertTrue(wallet.findByCustomerIdAndVoucherId(customer.getId(), voucher.getId()).isPresent());
        orderService.customerCancelOrder(order.getId(), customer.getId(), "Cancel after voucher deletion");
        em.flush(); em.clear();
        assertEquals(10, variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, vouchers.findById(voucher.getId()).orElseThrow().getUsedCount());
        assertTrue(vouchers.findById(voucher.getId()).orElseThrow().getIsDeleted());
    }

    @Test void deletedVoucherCannotBeSavedEditedActivatedReservedOrCheckedOut() {
        Voucher voucher = voucherService.createVoucher(voucherForm());
        voucherService.softDeleteVoucher(voucher.getId()); em.flush(); em.clear();
        assertFalse(voucherService.validateAndCalculateDiscount(voucher.getCode(), customer.getId(), new BigDecimal("400000")).isValid());
        assertTrue(vouchers.findAvailablePublicVouchers(LocalDateTime.now()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> voucherService.saveVoucherToWallet(customer.getId(), voucher.getId()));
        assertThrows(IllegalArgumentException.class, () -> voucherService.getVoucherById(voucher.getId()));
        assertThrows(IllegalArgumentException.class, () -> voucherService.updateVoucher(voucher.getId(), voucherForm()));
        assertThrows(IllegalArgumentException.class, () -> voucherService.toggleVoucherStatus(voucher.getId()));
        assertThrows(IllegalStateException.class, () -> voucherService.reserveVoucher(voucher.getId()));
        carts.addItemToCart(customer.getId(), variant.getId(), 2);
        var form = checkout(); form.setVoucherCode(voucher.getCode());
        assertThrows(IllegalArgumentException.class, () -> orderService.checkoutOrder(customer.getId(), form));
        assertEquals(0, orders.count());
        assertEquals(10, variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, vouchers.findById(voucher.getId()).orElseThrow().getUsedCount());
        assertEquals(1, carts.getCartForUser(customer.getId()).getItems().size());
    }

    @Test void deletedVoucherListRendersSeparatelyAndKeepsPaginationFilter() throws Exception {
        Voucher first = voucherService.createVoucher(voucherForm());
        var secondForm = voucherForm(); secondForm.setCode("REG11");
        Voucher second = voucherService.createVoucher(secondForm);
        var before = mvc.perform(get("/admin/vouchers").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(before.contains("/admin/vouchers/" + first.getId() + "/delete"));
        assertTrue(before.contains("name=\"_csrf\""));
        voucherService.softDeleteVoucher(first.getId()); voucherService.softDeleteVoucher(second.getId()); em.flush();
        assertEquals(0, voucherService.getAllVouchers(PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, voucherService.searchVouchers(null, null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(2, voucherService.searchVouchers(null, null, true, PageRequest.of(0, 10)).getTotalElements());
        var html = mvc.perform(get("/admin/vouchers?deleted=true&size=1").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Đã xóa"));
        assertTrue(html.contains("deleted=true"));
        assertFalse(html.contains("/delete\""));
        assertFalse(html.contains("/toggle-status\""));
        mvc.perform(get("/admin/vouchers/" + first.getId() + "/edit").with(user(new CustomUserDetails(admin))))
                .andExpect(redirectedUrl("/admin/vouchers")).andExpect(flash().attributeExists("errorMessage"));
    }

    @Test void onlyAdminWithCsrfCanSoftDeleteVoucher() throws Exception {
        Voucher voucher = voucherService.createVoucher(voucherForm());
        String path = "/admin/vouchers/" + voucher.getId() + "/delete";
        for (User denied : List.of(customer, staff)) {
            mvc.perform(post(path).with(user(new CustomUserDetails(denied))).with(csrf()))
                    .andExpect(redirectedUrl("/error/403"));
        }
        mvc.perform(post(path).with(csrf())).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post(path).with(user(new CustomUserDetails(admin))))
                .andExpect(redirectedUrl("/error/403"));
        assertFalse(vouchers.findById(voucher.getId()).orElseThrow().getIsDeleted());
        mvc.perform(post(path).with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(redirectedUrl("/admin/vouchers"));
        assertTrue(vouchers.findById(voucher.getId()).orElseThrow().getIsDeleted());
    }

    @Test void repeatedSoftDeletionKeepsTimestampAndReservesVoucherCode() {
        Voucher voucher = voucherService.createVoucher(voucherForm());
        voucherService.softDeleteVoucher(voucher.getId()); em.flush(); em.clear();
        var timestamp = vouchers.findById(voucher.getId()).orElseThrow().getDeletedAt();
        voucherService.softDeleteVoucher(voucher.getId()); em.flush(); em.clear();
        assertEquals(timestamp, vouchers.findById(voucher.getId()).orElseThrow().getDeletedAt());
        assertEquals(1, vouchers.count());
        assertThrows(IllegalArgumentException.class, () -> voucherService.createVoucher(voucherForm()));
    }

    @Test void adminEditAfterAtomicQuotaReservationKeepsUpdatedCount() {
        Voucher v=voucherService.createVoucher(voucherForm());
        voucherService.reserveVoucher(v.getId());
        var form=voucherForm();form.setTitle("Updated");
        voucherService.updateVoucher(v.getId(),form);em.flush();em.clear();
        assertEquals(1,vouchers.findById(v.getId()).orElseThrow().getUsedCount());
    }
}
