package com.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class StockTransactionRequest {
    @NotBlank(message = "Product name is required")
    private String productName;

    @NotBlank(message = "Type must be IN or OUT")
    private String type;

    @NotNull(message = "Quantity is required")
    private BigDecimal quantity;

    private String unit;
    private BigDecimal pricePerUnit;
    private String note;
}
