package com.petshop.service;

import com.petshop.dto.stock.StockImportFormDto;
import com.petshop.entity.ProductVariant;
import com.petshop.entity.StockImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StockImportService {

    StockImport createStockImport(StockImportFormDto form, Long staffId);  // ← ĐỔI signature
    Page<StockImport> getStockImports(Pageable pageable);
    StockImport getStockImportDetail(Long id);
    List<ProductVariant> getLowStockVariants();

}