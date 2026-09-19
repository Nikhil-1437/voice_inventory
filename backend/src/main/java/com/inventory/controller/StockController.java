package com.inventory.controller;

import com.inventory.dto.ProductDto;
import com.inventory.dto.StockTransactionRequest;
import com.inventory.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping("/transaction")
    public ProductDto recordTransaction(@Valid @RequestBody StockTransactionRequest request) {
        return stockService.recordTransaction(request);
    }

    @GetMapping("/history/{productId}")
    public List<Map<String, Object>> getHistory(@PathVariable Long productId) {
        return stockService.getHistory(productId);
    }

    @GetMapping("/activity")
    public List<Map<String, Object>> getRecentActivity() {
        return stockService.getRecentActivity();
    }
}
