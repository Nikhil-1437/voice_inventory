package com.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    private String name;
    private String localName;
    private String category;
    @NotBlank(message = "Unit is required")
    private String unit;
    private BigDecimal quantity;
    private BigDecimal pricePerUnit;
    private BigDecimal lowStockThreshold;
}
