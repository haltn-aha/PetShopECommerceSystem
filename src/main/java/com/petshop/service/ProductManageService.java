
package com.petshop.service;

import com.petshop.entity.Product;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductManageService {

    Page<Product> getProducts(Pageable pageable);

    Page<Product> searchProducts(String keyword, Pageable pageable);

    Page<Product> searchProducts(
            String keyword,
            Integer categoryId,
            Pageable pageable
    );

    Product findById(Long id);

    Product createProduct(Product product, Long staffId);

    Product updateProduct(Long id, Product product, Long staffId);

    void toggleProductStatus(Long id, ProductStatus status);

    void deleteProduct(Long id);

    ProductVariant addVariant(Long productId, ProductVariant variant);

    ProductVariant updateVariant(Long variantId, ProductVariant variant);

    List<ProductVariant> getLowStockVariants();
}
