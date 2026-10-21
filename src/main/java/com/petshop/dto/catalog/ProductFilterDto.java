package com.petshop.dto.catalog;

import com.petshop.entity.enums.PetType;
import com.petshop.entity.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterDto {
    private String keyword;
    private String categoryCode;
    private Long categoryId;
    private Long brandId;
    private PetType petType;
    private ProductStatus status; // Bổ sung trường này để hết lỗi getStatus()
    private String sortBy;
}