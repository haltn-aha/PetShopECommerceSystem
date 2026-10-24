package com.petshop.controller.guest;

import com.petshop.dto.catalog.ProductFilterDto;
import com.petshop.dto.catalog.ProductSummaryDto;
import com.petshop.entity.enums.PetType;
import com.petshop.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.petshop.entity.Product;

@Controller
@RequiredArgsConstructor
public class ProductCatalogController {

    private final CatalogService catalogService;

    @GetMapping("/products")
    public String listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer brandId,
            @RequestParam(required = false) String petType,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model) {

        PetType petTypeEnum = null;
        if (petType != null && !petType.trim().isEmpty()) {
            try {
                petTypeEnum = PetType.valueOf(petType.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        ProductFilterDto filter = ProductFilterDto.builder()
                .keyword(keyword)
                .categoryCode(categoryCode)
                .categoryId(categoryId == null ? null : categoryId.longValue())
                .brandId(brandId == null ? null : brandId.longValue())
                .petType(petTypeEnum)
                .sortBy(sortBy)
                .build();

        Pageable pageable = PageRequest.of(page, size);
        Page<ProductSummaryDto> productPage = catalogService.searchAndFilterProducts(filter, pageable);

        model.addAttribute("productPage", productPage);
        model.addAttribute("filter", filter);
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("brands", catalogService.getAllBrands());
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("selectedCategory", categoryCode != null ? categoryCode : "");
        model.addAttribute("selectedPetType", petType != null ? petType : "");
        model.addAttribute("selectedSort", sortBy != null ? sortBy : "newest");

        return "guest/product-list";
    }


    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable String slug, Model model) {
        Product product = catalogService.getProductBySlug(slug);

        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", catalogService.getFeaturedProducts(4));

        return "guest/product-detail";
    }


    @GetMapping("/categories/{code}")
    public String categoryShortcut(@PathVariable String code) {
        return "redirect:/products?categoryCode=" + code;
    }
}