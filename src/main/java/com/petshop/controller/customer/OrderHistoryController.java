package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.Order;
import com.petshop.entity.enums.OrderStatus;
import com.petshop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer/orders")
@RequiredArgsConstructor
public class OrderHistoryController {

    private final OrderService orderService;
    private final com.petshop.repository.OrderRepository orderRepository;
    private final com.petshop.repository.OrderItemRepository orderItemRepository;
    private final com.petshop.repository.OrderStatusHistoryRepository statusHistoryRepository;
    private final com.petshop.repository.PaymentTransactionRepository paymentTransactionRepository;

    @GetMapping
    public String listOrders(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @RequestParam(required = false) OrderStatus status,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size,
                             Model model) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<Order> orders = orderService.getCustomerOrders(userDetails.getId(), status, pageable);

        model.addAttribute("orders", orders);
        model.addAttribute("currentStatus", status);
        model.addAttribute("totalOrdersCount", orderRepository.countByCustomerId(userDetails.getId()));
        model.addAttribute("pendingCount", orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.PENDING_CONFIRMATION)
                + orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.PENDING_PAYMENT));
        model.addAttribute("shippingCount", orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.SHIPPING)
                + orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.PREPARING));
        model.addAttribute("completedCount", orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.COMPLETED));
        model.addAttribute("cancelledCount", orderRepository.countByCustomerIdAndOrderStatus(userDetails.getId(), OrderStatus.CANCELLED));
        return "customer/order-list";
    }

    @GetMapping("/{id}")
    public String orderDetail(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @PathVariable Long id,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.getOrderDetailForCustomer(id, userDetails.getId());
            model.addAttribute("order", order);
            model.addAttribute("items", orderItemRepository.findByOrderId(id));
            model.addAttribute("statusHistories", statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(id));
            model.addAttribute("paymentTransactions", paymentTransactionRepository.findByOrderIdOrderByCreatedAtDesc(id));
            return "customer/order-detail";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/customer/orders";
        }
    }

    @PostMapping("/{id}/cancel")
    public String cancelOrder(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @PathVariable Long id,
                              @RequestParam(defaultValue = "Khách hàng yêu cầu hủy đơn") String reason,
                              RedirectAttributes redirectAttributes) {
        try {
            orderService.customerCancelOrder(id, userDetails.getId(), reason);
            redirectAttributes.addFlashAttribute("successMessage", "Hủy đơn hàng thành công!");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customer/orders/" + id;
    }
}
