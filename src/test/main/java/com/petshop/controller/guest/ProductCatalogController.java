@GetMapping("/products/{id}")
public String getProductDetail(@PathVariable Long id, Model model) {
    Product product = productRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm ID: " + id));

    List<ProductVariant> variants = productVariantRepository.findByProduct_Id(id);

    model.addAttribute("product", product);
    model.addAttribute("variants", variants);
    return "guest/product-detail";
}