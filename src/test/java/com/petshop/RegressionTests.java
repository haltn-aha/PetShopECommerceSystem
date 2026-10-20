package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.auth.*;
import com.petshop.dto.admin.StaffCreateRequestDto;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegressionTests {
    @org.springframework.boot.test.mock.mockito.MockBean EmailService emailService;
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
    @Test void staffInvitationRequiresEmailAndSendsSetupLink() {
        long count=users.count();
        var form=StaffCreateRequestDto.builder().fullName("New staff").email("").phone("0901000004").build();
        assertThrows(IllegalArgumentException.class,()->userService.createStaffAccount(form));
        assertEquals(count,users.count());
        form.setEmail("new@regression.test");
        var created = userService.createStaffAccount(form);
        assertEquals("STAFF",created.getRole().getCode());
        assertTrue(created.getMustChangePassword());
        verify(emailService).sendStaffInvitation(eq(form.getEmail()), eq("New staff"), contains("/reset-password?token="));
        assertEquals(1, tokens.count());
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
    @Test void resetPasswordRevokesSessionAndEmailsOneTimeLink() throws Exception {
        var old=new CustomUserDetails(customer);userService.resetUserPassword(customer.getId(),admin.getId());
        assertTrue(users.findById(customer.getId()).orElseThrow().getMustChangePassword());
        mvc.perform(get("/account/profile").with(user(old))).andExpect(redirectedUrl("/login?expired=true"));
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(customer.getEmail()), anyString(), link.capture());
        String token = java.net.URI.create(link.getValue()).getQuery().substring("token=".length());
        mvc.perform(post("/reset-password").with(csrf()).param("token",token).param("newPassword","Changed@123").param("confirmPassword","Changed@123"))
                .andExpect(redirectedUrl("/login"));
        assertFalse(users.findById(customer.getId()).orElseThrow().getMustChangePassword());
        assertTrue(encoder.matches("Changed@123",users.findById(customer.getId()).orElseThrow().getPasswordHash()));
        assertFalse(auth.verifyPasswordResetToken(token));
    }

    @Test void staffCanReceiveEmailSetPasswordAndLoginWithoutAdminChoosingPassword() throws Exception {
        String html = mvc.perform(get("/admin/users/create").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("type=\"password\""));
        mvc.perform(post("/admin/users/create").with(user(new CustomUserDetails(admin))).with(csrf())
                .param("fullName","Staff invited").param("email","invite@regression.test").param("phone","0901000005")
                .param("roleCode","ADMIN").param("password","Attacker@123"))
                .andExpect(redirectedUrl("/admin/users"));
        var created = users.findByEmail("invite@regression.test").orElseThrow();
        assertEquals("STAFF",created.getRole().getCode());
        assertFalse(encoder.matches("Attacker@123",created.getPasswordHash()));
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendStaffInvitation(eq(created.getEmail()),eq("Staff invited"),link.capture());
        String token = java.net.URI.create(link.getValue()).getQuery().substring(6);
        assertTrue(auth.verifyPasswordResetToken(token));
        assertNotEquals(token,tokens.findAll().get(0).getTokenHash());
        mvc.perform(post("/reset-password").with(csrf()).param("token",token).param("newPassword","StaffOwn@123").param("confirmPassword","StaffOwn@123"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin().user(created.getEmail()).password("StaffOwn@123"))
                .andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated().withRoles("STAFF"));
        assertFalse(users.findById(created.getId()).orElseThrow().getMustChangePassword());
        assertThrows(IllegalArgumentException.class,()->auth.resetPasswordWithToken(token,"Reuse@123"));
    }

    @Test void resendingStaffInvitationExpiresOldLinkAndRejectsNonPendingAccounts() throws Exception {
        var dto = StaffCreateRequestDto.builder().fullName("Staff invited").email("resend@regression.test").phone("0901000006").build();
        var created = userService.createStaffAccount(dto);
        userService.resendStaffInvitation(created.getId());
        var links = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService,times(2)).sendStaffInvitation(eq(created.getEmail()),anyString(),links.capture());
        String old = java.net.URI.create(links.getAllValues().get(0)).getQuery().substring(6);
        String latest = java.net.URI.create(links.getAllValues().get(1)).getQuery().substring(6);
        assertFalse(auth.verifyPasswordResetToken(old));
        assertThrows(IllegalArgumentException.class,()->auth.resetPasswordWithToken(old,"Expired@123"));
        assertTrue(auth.verifyPasswordResetToken(latest));
        auth.resetPasswordWithToken(latest,"Chosen@123");
        assertThrows(IllegalArgumentException.class,()->userService.resendStaffInvitation(created.getId()));
        assertThrows(IllegalArgumentException.class,()->userService.resendStaffInvitation(customer.getId()));
    }

    @Test void unavailableMailShowsErrorAndDoesNotCreateStaff() throws Exception {
        doThrow(new IllegalStateException("SMTP chưa cấu hình")).when(emailService).requireConfigured();
        mvc.perform(post("/admin/users/create").with(user(new CustomUserDetails(admin))).with(csrf())
                .param("fullName","Staff invited").param("email","failure@regression.test").param("phone","0901000007"))
                .andExpect(view().name("admin/user-form")).andExpect(model().attributeExists("errorMessage"));
        assertFalse(users.existsByEmail("failure@regression.test"));
        assertEquals(0,tokens.count());
    }

    @Test void changingInvitedStaffEmailRevokesPreviouslySentLink() {
        var created=userService.createStaffAccount(StaffCreateRequestDto.builder().fullName("Staff pending")
                .email("previous@regression.test").phone("0901000010").build());
        var links=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendStaffInvitation(eq(created.getEmail()),anyString(),links.capture());
        String previous=java.net.URI.create(links.getValue()).getQuery().substring(6);
        userService.updateUserByAdmin(created.getId(),com.petshop.dto.admin.UserAdminUpdateDto.builder()
                .fullName(created.getFullName()).email("changed@regression.test").phone(created.getPhone()).status("ACTIVE").build(),admin.getId());
        assertFalse(auth.verifyPasswordResetToken(previous));
        userService.resendStaffInvitation(created.getId());
        verify(emailService).sendStaffInvitation(eq("changed@regression.test"),anyString(),contains("/reset-password?token="));
    }

    @Test void accountEditingCannotChangeRolesOrReassignRelatedData() throws Exception {
        carts.addItemToCart(customer.getId(),variant.getId(),1);
        var order=orderService.checkoutOrder(customer.getId(),checkout());
        product.setCreatedBy(staff);products.saveAndFlush(product);
        for (User target : List.of(customer,staff)) {
            mvc.perform(post("/admin/users/"+target.getId()+"/edit").with(user(new CustomUserDetails(admin))).with(csrf())
                    .param("fullName",target.getFullName()).param("email",target.getEmail()).param("phone",target.getPhone())
                    .param("status","ACTIVE").param("roleCode",target==customer?"STAFF":"CUSTOMER").param("roleId",admin.getRole().getId().toString()))
                    .andExpect(redirectedUrl("/admin/users"));
        }
        em.flush();em.clear();
        assertEquals("CUSTOMER",users.findById(customer.getId()).orElseThrow().getRole().getCode());
        assertEquals("STAFF",users.findById(staff.getId()).orElseThrow().getRole().getCode());
        assertEquals(customer.getId(),orders.findById(order.getId()).orElseThrow().getCustomer().getId());
        assertEquals(staff.getId(),products.findById(product.getId()).orElseThrow().getCreatedBy().getId());
        String html=mvc.perform(get("/admin/users/"+staff.getId()+"/edit").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("name=\"roleCode\""));
        assertFalse(html.contains("name=\"roleId\""));
    }

    @Test void publicForgotPasswordDoesNotDisableCurrentLoginAndLockedTokenCannotSetPassword() {
        String original=customer.getPasswordHash();
        auth.initiatePasswordReset(customer.getEmail());
        var links=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(customer.getEmail()),anyString(),links.capture());
        String token=java.net.URI.create(links.getValue()).getQuery().substring(6);
        assertEquals(original,users.findById(customer.getId()).orElseThrow().getPasswordHash());
        assertFalse(customer.getMustChangePassword());
        customer.setStatus("LOCKED");users.saveAndFlush(customer);
        assertFalse(auth.verifyPasswordResetToken(token));
        assertThrows(IllegalArgumentException.class,()->auth.resetPasswordWithToken(token,"Locked@123"));
        assertEquals("LOCKED",users.findById(customer.getId()).orElseThrow().getStatus());
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
