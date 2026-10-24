
package com.petshop.service.impl;

import com.petshop.dto.catalog.ProductFilterDto;
import com.petshop.dto.catalog.ProductSummaryDto;
import com.petshop.entity.Brand;
import com.petshop.entity.Category;
import com.petshop.entity.Product;
import com.petshop.entity.ProductImage;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.repository.BrandRepository;
import com.petshop.repository.CategoryRepository;
import com.petshop.repository.ProductRepository;
import com.petshop.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<Product> filterProducts(
            String keyword,
            Long categoryId,
            Pageable pageable) {

        Specification<Product> spec = (root, query, cb) -> {
            var predicate = cb.equal(
                    root.get("status"),
                    ProductStatus.ACTIVE
            );

            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = "%" + keyword.trim().toLowerCase() + "%";

                predicate = cb.and(
                        predicate,
                        cb.or(
                                cb.like(cb.lower(root.get("name")), kw),
                                cb.like(cb.lower(
                                        root.get("shortDescription")), kw)
                        )
                );
            }

            if (categoryId != null) {
                predicate = cb.and(
                        predicate,
                        cb.equal(
                                root.get("category").get("id"),
                                categoryId
                        )
                );
            }

            return predicate;
        };

        return productRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryDto> searchAndFilterProducts(
            ProductFilterDto filter,
            Pageable pageable) {

        Specification<Product> spec = (root, query, cb) -> {
            var predicate = cb.equal(
                    root.get("status"),
                    ProductStatus.ACTIVE
            );

            if (filter == null) {
                return predicate;
            }

            if (filter.getKeyword() != null
                    && !filter.getKeyword().trim().isEmpty()) {

                String keyword = "%"
                        + filter.getKeyword().trim().toLowerCase()
                        + "%";

                predicate = cb.and(
                        predicate,
                        cb.or(
                                cb.like(
                                        cb.lower(root.get("name")),
                                        keyword
                                ),
                                cb.like(
                                        cb.lower(root.get("shortDescription")),
                                        keyword
                                )
                        )
                );
            }

            if (filter.getCategoryId() != null) {
                predicate = cb.and(
                        predicate,
                        cb.equal(
                                root.get("category").get("id"),
                                filter.getCategoryId()
                        )
                );
            }

            if (filter.getBrandId() != null) {
                predicate = cb.and(
                        predicate,
                        cb.equal(
                                root.get("brand").get("id"),
                                filter.getBrandId()
                        )
                );
            }

            if (filter.getPetType() != null) {
                predicate = cb.and(
                        predicate,
                        cb.equal(
                                root.get("petType"),
                                filter.getPetType()
                        )
                );
            }

            return predicate;
        };

        Sort sort = Sort.by(
                Sort.Direction.DESC,
                "createdAt"
        );

        if (filter != null && filter.getSortBy() != null) {
            switch (filter.getSortBy()) {
                case "asc":
                    sort = Sort.by(Sort.Direction.ASC, "name");
                    break;
                case "desc":
                    sort = Sort.by(Sort.Direction.DESC, "name");
                    break;
                case "newest":
                    sort = Sort.by(Sort.Direction.DESC, "createdAt");
                    break;
                default:
                    break;
            }
        }

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort
        );

        Page<Product> products =
                productRepository.findAll(spec, sortedPageable);

        return products.map(this::mapToSummaryDto);
    }


    @Override
    @Transactional(readOnly = true)
    public Product getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy sản phẩm: " + slug
                ));

        // Initialize images while the Hibernate session is open.
        product.getImages().size();

        // Initialize variants while the Hibernate session is open.
        product.getVariants().size();

        return product;
    }


    @Override
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getActiveCategoryTree() {
        return categoryRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSummaryDto> getFeaturedProducts(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        Pageable pageable = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Product> products = productRepository.findByStatus(
                ProductStatus.ACTIVE,
                pageable
        );

        return products.getContent()
                .stream()
                .map(this::mapToSummaryDto)
                .toList();
    }

    private ProductSummaryDto mapToSummaryDto(Product product) {
        String primaryImageUrl = null;

        if (product.getImages() != null
                && !product.getImages().isEmpty()) {

            // Prefer the primary image, ordered by SortOrder.
            primaryImageUrl = product.getImages()
                    .stream()
                    .filter(image ->
                            Boolean.TRUE.equals(image.getIsPrimary()))
                    .sorted((a, b) -> Integer.compare(
                            a.getSortOrder() == null ? 0 : a.getSortOrder(),
                            b.getSortOrder() == null ? 0 : b.getSortOrder()
                    ))
                    .map(ProductImage::getImageUrl)
                    .filter(url -> url != null && !url.trim().isEmpty())
                    .findFirst()
                    .orElse(null);

            // Fall back to the first available image.
            if (primaryImageUrl == null) {
                primaryImageUrl = product.getImages()
                        .stream()
                        .sorted((a, b) -> Integer.compare(
                                a.getSortOrder() == null ? 0 : a.getSortOrder(),
                                b.getSortOrder() == null ? 0 : b.getSortOrder()
                        ))
                        .map(ProductImage::getImageUrl)
                        .filter(url -> url != null && !url.trim().isEmpty())
                        .findFirst()
                        .orElse(null);
            }
        }

        return ProductSummaryDto.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .categoryName(
                        product.getCategory() != null
                                ? product.getCategory().getName()
                                : ""
                )
                .brandName(
                        product.getBrand() != null
                                ? product.getBrand().getName()
                                : ""
                )
                .petType(product.getPetType())
                .primaryImageUrl(primaryImageUrl)
                .imageUrl(primaryImageUrl)
                .build();
    }
}
