
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

    // Display and filter products
    @GetMapping
    public String listStaffProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(size, 1)
        );

        Integer selectedCategoryId =
                categoryId == null ? null : categoryId.intValue();

        Page<Product> products = productManageService.searchProducts(
                keyword, selectedCategoryId, pageable
        );

        model.addAttribute("products", products);
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("categories", catalogService.getAllCategories());

        return "staff/product-list";
    }

    // Display product creation form
    @GetMapping("/create")
    public String createForm(Model model) {
        if (!model.containsAttribute("product")) {
            model.addAttribute("product", new Product());
        }

        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("brands", catalogService.getAllBrands());

        return "staff/product-form";
    }

    // Create a product
    @PostMapping("/create")
    public String saveProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ModelAttribute Product product,
            RedirectAttributes redirectAttributes) {

        productManageService.createProduct(product, userDetails.getId());

        redirectAttributes.addFlashAttribute(
                "successMessage", "Tạo sản phẩm thành công!"
        );

        return "redirect:/staff/products";
    }

    // Display product editing form
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("product", productManageService.findById(id));
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("brands", catalogService.getAllBrands());

        return "staff/product-form";
    }

    // Update a product
    @PostMapping("/{id}/edit")
    public String updateProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @ModelAttribute Product product,
            RedirectAttributes redirectAttributes) {

        productManageService.updateProduct(
                id, product, userDetails.getId()
        );

        redirectAttributes.addFlashAttribute(
                "successMessage", "Cập nhật sản phẩm thành công!"
        );

        return "redirect:/staff/products";
    }

    // Update product status
    @PostMapping("/{id}/status")
    public String toggleStatus(
            @PathVariable Long id,
            @RequestParam ProductStatus status,
            RedirectAttributes redirectAttributes) {

        productManageService.toggleProductStatus(id, status);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Cập nhật trạng thái thành công!"
        );

        return "redirect:/staff/products";
    }

    // Display product variants
    @GetMapping("/{id}/variants")
    public String manageVariants(@PathVariable Long id, Model model) {
        Product product = productManageService.findById(id);

        model.addAttribute("product", product);
        model.addAttribute("newVariant", new ProductVariant());

        return "staff/variant-list";
    }

    // Add a product variant
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

    // Update a product variant
    @PostMapping("/variants/{vid}/edit")
    public String updateVariant(
            @PathVariable Long vid,
            @RequestParam Long productId,
            @ModelAttribute ProductVariant variant,
            RedirectAttributes redirectAttributes) {

        productManageService.updateVariant(vid, variant);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Cập nhật biến thể thành công!"
        );

        return "redirect:/staff/products/" + productId + "/variants";
    }
}