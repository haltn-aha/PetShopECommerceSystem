package com.petshop.controller.staff;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.Product;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.service.CatalogService;
import com.petshop.service.ProductManageService;
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
@RequestMapping("/staff/products")
@RequiredArgsConstructor
public class ProductManageController {

    private final ProductManageService productManageService;
    private final CatalogService catalogService;

    // Hiển thị danh sách sản phẩm dành cho Staff
    @GetMapping
    public String listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Pageable pageable = PageRequest.of(safePage, safeSize);

        Integer selectedCategoryId =
                categoryId == null ? null : categoryId.intValue();

        Page<Product> products = productManageService.searchProducts(
                keyword,
                selectedCategoryId,
                pageable
        );

        model.addAttribute("products", products);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("categories", catalogService.getAllCategories());

        return "staff/product-list";
    }

    // Xem chi tiết sản phẩm
    @GetMapping("/{id}")
    public String viewProduct(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            model.addAttribute("product", productManageService.findById(id));
            return "staff/product-detail";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Không tìm thấy sản phẩm."
            );
            return "redirect:/staff/products";
        }
    }

    // Form tạo mới sản phẩm
    @GetMapping("/create")
    public String createForm(Model model) {
        if (!model.containsAttribute("product")) {
            model.addAttribute("product", new Product());
        }

        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("brands", catalogService.getAllBrands());

        return "staff/product-form";
    }

    // Xử lý lưu sản phẩm mới
    @PostMapping("/create")
    public String saveProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ModelAttribute Product product,
            RedirectAttributes redirectAttributes) {

        try {
            if (userDetails == null) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage", "Vui lòng đăng nhập lại."
                );
                return "redirect:/login";
            }

            productManageService.createProduct(product, userDetails.getId());

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Tạo sản phẩm thành công!"
            );

            return "redirect:/staff/products";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", e.getMessage()
            );
            redirectAttributes.addFlashAttribute("product", product);

            return "redirect:/staff/products/create";
        }
    }

    // Form chỉnh sửa sản phẩm
    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            model.addAttribute("product", productManageService.findById(id));
            model.addAttribute("categories", catalogService.getAllCategories());
            model.addAttribute("brands", catalogService.getAllBrands());

            return "staff/product-form";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Không tìm thấy sản phẩm."
            );
            return "redirect:/staff/products";
        }
    }

    // Xử lý cập nhật sản phẩm
    @PostMapping("/{id}/edit")
    public String updateProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @ModelAttribute Product product,
            RedirectAttributes redirectAttributes) {

        try {
            if (userDetails == null) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage", "Vui lòng đăng nhập lại."
                );
                return "redirect:/login";
            }

            productManageService.updateProduct(
                    id, product, userDetails.getId()
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Cập nhật sản phẩm thành công!"
            );

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", e.getMessage()
            );
            return "redirect:/staff/products/" + id + "/edit";
        }

        return "redirect:/staff/products";
    }

    // Cập nhật trạng thái ẩn/hiện sản phẩm
    @PostMapping("/{id}/status")
    public String updateProductStatus(
            @PathVariable Long id,
            @RequestParam ProductStatus status,
            RedirectAttributes redirectAttributes) {

        try {
            productManageService.toggleProductStatus(id, status);

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Cập nhật trạng thái thành công!"
            );

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", e.getMessage()
            );
        }

        return "redirect:/staff/products";
    }

    // Xóa mềm sản phẩm (chuyển sang trạng thái ẩn)
    @PostMapping("/{id}/delete")
    public String hideProduct(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            productManageService.deleteProduct(id);

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Đã ẩn sản phẩm thành công!"
            );

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", e.getMessage()
            );
        }

        return "redirect:/staff/products";
    }

    // Hiển thị danh sách biến thể sản phẩm (SKU)
    @GetMapping("/{id}/variants")
    public String manageVariants(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            Product product = productManageService.findById(id);

            model.addAttribute("product", product);
            model.addAttribute("newVariant", new ProductVariant());

            return "staff/variant-list";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Không tìm thấy sản phẩm."
            );
            return "redirect:/staff/products";
        }
    }

    // Thêm biến thể cho sản phẩm
    @PostMapping("/{id}/variants")
    public String addVariant(
            @PathVariable Long id,
            @ModelAttribute ProductVariant variant,
            RedirectAttributes redirectAttributes) {

        try {
            productManageService.addVariant(id, variant);

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Thêm biến thể SKU thành công!"
            );

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", e.getMessage()
            );
        }

        return "redirect:/staff/products/" + id + "/variants";
    }

    // Cập nhật biến thể sản phẩm
    @PostMapping("/variants/{vid}/edit")
    public String updateVariant(
            @PathVariable Long vid,
            @RequestParam Long productId,
            @ModelAttribute ProductVariant variant,
            RedirectAttributes redirectAttributes) {

        try {
            productManageService.updateVariant(vid, variant);

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Cập nhật biến thể thành công!"
            );

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", e.getMessage()
            );
        }

        return "redirect:/staff/products/" + productId + "/variants";
    }
}