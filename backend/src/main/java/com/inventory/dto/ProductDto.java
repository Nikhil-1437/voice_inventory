package com.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDto {
    private Long id;
    private String name;
    private String localName;
    private String category;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal pricePerUnit;
    private BigDecimal lowStockThreshold;
    private boolean lowStock;
}
