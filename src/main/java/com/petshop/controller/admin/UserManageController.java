package com.petshop.controller.admin;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.auth.RegisterRequestDto;
import com.petshop.entity.User;
import com.petshop.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserManageController {

    private final UserService userService;

    @GetMapping
    public String listUsers(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String role,
                            @RequestParam(required = false) String status,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            Model model) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<User> users = userService.getAllUsers(keyword, role, status, pageable);

        model.addAttribute("users", users);
        model.addAttribute("keyword", keyword);
        model.addAttribute("role", role);
        model.addAttribute("status", status);
        return "admin/user-list";
    }

    @GetMapping("/create")
    public String createStaffForm(Model model) {
        if (!model.containsAttribute("staffForm")) {
            model.addAttribute("staffForm", new RegisterRequestDto());
        }
        return "admin/user-form";
    }

    @PostMapping("/create")
    public String saveStaff(@Valid @ModelAttribute("staffForm") RegisterRequestDto staffForm,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes,
                            Model model) {
        if (bindingResult.hasErrors()) {
            return "admin/user-form";
        }

        try {
            userService.createStaffAccount(staffForm);
            redirectAttributes.addFlashAttribute("successMessage", "Cấp tài khoản nhân viên thành công!");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/user-form";
        }
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleUserStatus(@AuthenticationPrincipal CustomUserDetails userDetails,
                                   @PathVariable Long id,
                                   RedirectAttributes redirectAttributes) {
        try {
            userService.toggleUserStatus(id, userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Thay đổi trạng thái tài khoản thành công!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id);
        com.petshop.dto.admin.UserAdminUpdateDto updateDto = com.petshop.dto.admin.UserAdminUpdateDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .roleCode(user.getRole().getCode())
                .status(user.getStatus())
                .build();
        model.addAttribute("userForm", updateDto);
        model.addAttribute("targetUser", user);
        return "admin/user-edit";
    }

    @PostMapping("/{id}/edit")
    public String updateUser(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @PathVariable Long id,
                             @Valid @ModelAttribute("userForm") com.petshop.dto.admin.UserAdminUpdateDto userForm,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("targetUser", userService.findById(id));
            return "admin/user-edit";
        }

        try {
            userService.updateUserByAdmin(id, userForm, userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin tài khoản thành công!");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("targetUser", userService.findById(id));
            return "admin/user-edit";
        }
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        try {
            String tempPassword = userService.resetUserPassword(id, userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Đã đặt lại mật khẩu thành công! Mật khẩu mới là: " + tempPassword + " (Vui lòng lưu lại và gửi cho người dùng).");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể đặt lại mật khẩu: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
