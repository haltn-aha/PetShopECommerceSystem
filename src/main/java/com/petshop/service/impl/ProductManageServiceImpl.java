package com.petshop.service.impl;

import com.petshop.entity.Brand;
import com.petshop.entity.Category;
import com.petshop.entity.Product;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.entity.enums.ProductVariantStatus;
import com.petshop.entity.enums.PetType;
import com.petshop.repository.BrandRepository;
import com.petshop.repository.CategoryRepository;
import com.petshop.repository.ProductRepository;
import com.petshop.repository.ProductVariantRepository;
import com.petshop.service.ProductManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductManageServiceImpl implements ProductManageService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> searchProducts(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return productRepository.findAll(pageable);
        }

        return productRepository.findByNameContainingIgnoreCase(
                keyword.trim(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> searchProducts(
            String keyword, Integer categoryId, Pageable pageable) {

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = categoryId != null;

        if (hasKeyword && hasCategory) {
            return productRepository.findByNameContainingIgnoreCaseAndCategory_Id(
                    keyword.trim(), categoryId, pageable);
        }

        if (hasKeyword) {
            return productRepository.findByNameContainingIgnoreCase(
                    keyword.trim(), pageable);
        }

        if (hasCategory) {
            return productRepository.findByCategory_Id(categoryId, pageable);
        }

        return productRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found: " + id));
    }

    @Override
    public Product createProduct(Product product, Long staffId) {
        if (product == null) {
            throw new IllegalArgumentException("Product is required.");
        }

        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is required.");
        }

        product.setName(product.getName().trim());
        product.setCategory(getCategory(product));
        product.setBrand(getBrand(product));
        product.setSlug(createSlug(product.getName()));

        if (product.getStatus() == null) {
            product.setStatus(ProductStatus.ACTIVE);
        }

        if (product.getPetType() == null) {
            product.setPetType(PetType.BOTH);
        }

        return productRepository.save(product);
    }

    @Override
    public Product updateProduct(Long id, Product product, Long staffId) {
        if (product == null) {
            throw new IllegalArgumentException("Product is required.");
        }

        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is required.");
        }

        Product existing = findById(id);
        String oldName = existing.getName();
        String newName = product.getName().trim();

        existing.setName(newName);

        if (product.getPetType() != null) {
            existing.setPetType(product.getPetType());
        }

        if (product.getStatus() != null) {
            existing.setStatus(product.getStatus());
        }

        existing.setCategory(getCategory(product));
        existing.setBrand(getBrand(product));

        if (!newName.equals(oldName)) {
            existing.setSlug(createSlug(newName));
        }

        return productRepository.save(existing);
    }

    @Override
    public void toggleProductStatus(Long id, ProductStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Product status is required.");
        }

        Product product = findById(id);
        product.setStatus(status);
        productRepository.save(product);
    }

    @Override
    public ProductVariant addVariant(Long productId, ProductVariant variant) {
        if (variant == null) {
            throw new IllegalArgumentException("Product variant is required.");
        }

        Product product = findById(productId);

        if (variant.getSkuCode() == null || variant.getSkuCode().isBlank()) {
            throw new IllegalArgumentException("SKU code is required.");
        }

        String sku = variant.getSkuCode().trim();

        if (productVariantRepository.existsBySkuCode(sku)) {
            throw new IllegalArgumentException("SKU code already exists.");
        }

        if (variant.getVariantName() == null
                || variant.getVariantName().isBlank()) {
            throw new IllegalArgumentException("Variant name is required.");
        }

        if (variant.getPrice() == null || variant.getPrice().signum() < 0) {
            throw new IllegalArgumentException(
                    "Variant price must be zero or greater.");
        }

        variant.setProduct(product);
        variant.setSkuCode(sku);

        if (variant.getStockQuantity() == null) {
            variant.setStockQuantity(0);
        }

        if (variant.getLowStockThreshold() == null) {
            variant.setLowStockThreshold(5);
        }

        if (variant.getStatus() == null) {
            variant.setStatus(ProductVariantStatus.ACTIVE);
        }

        return productVariantRepository.save(variant);
    }

    @Override
    public ProductVariant updateVariant(Long variantId, ProductVariant variant) {
        if (variant == null) {
            throw new IllegalArgumentException("Product variant is required.");
        }

        ProductVariant existing = productVariantRepository.findById(variantId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Variant not found: " + variantId));

        if (variant.getSkuCode() == null || variant.getSkuCode().isBlank()) {
            throw new IllegalArgumentException("SKU code is required.");
        }

        String sku = variant.getSkuCode().trim();

        boolean duplicateSku = productVariantRepository.existsBySkuCode(sku)
                && !sku.equals(existing.getSkuCode());

        if (duplicateSku) {
            throw new IllegalArgumentException("SKU code already exists.");
        }

        if (variant.getVariantName() == null
                || variant.getVariantName().isBlank()) {
            throw new IllegalArgumentException("Variant name is required.");
        }

        if (variant.getPrice() == null || variant.getPrice().signum() < 0) {
            throw new IllegalArgumentException(
                    "Variant price must be zero or greater.");
        }

        existing.setSkuCode(sku);
        existing.setVariantName(variant.getVariantName().trim());
        existing.setPrice(variant.getPrice());
        existing.setOriginalPrice(variant.getOriginalPrice());

        if (variant.getStockQuantity() != null) {
            existing.setStockQuantity(variant.getStockQuantity());
        }

        if (variant.getLowStockThreshold() != null) {
            existing.setLowStockThreshold(variant.getLowStockThreshold());
        }

        if (variant.getStatus() != null) {
            existing.setStatus(variant.getStatus());
        }

        return productVariantRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariant> getLowStockVariants() {
        return productVariantRepository.findLowStockVariants();
    }

    private Category getCategory(Product product) {
        if (product.getCategory() == null
                || product.getCategory().getId() == null) {
            throw new IllegalArgumentException("Please select a category.");
        }

        Integer categoryId = product.getCategory().getId();

        return categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Category not found: " + categoryId));
    }

    private Brand getBrand(Product product) {
        if (product.getBrand() == null
                || product.getBrand().getId() == null) {
            return null;
        }

        Integer brandId = product.getBrand().getId();

        return brandRepository.findById(brandId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Brand not found: " + brandId));
    }

    private String createSlug(String name) {
        String normalized = name.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");

        if (normalized.isBlank()) {
            normalized = "product";
        }

        return normalized + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }
}