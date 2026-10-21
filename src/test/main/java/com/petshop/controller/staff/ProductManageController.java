
@GetMapping("/products")
public String listProducts(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long categoryId,
        Model model) {

    model.addAttribute("keyword", keyword);
    model.addAttribute("selectedCategoryId", categoryId);


    model.addAttribute("categories", categoryService.getAllActiveCategories());


    model.addAttribute("products",
            productService.searchProducts(keyword, categoryId));

    return "guest/product-list";
}
