package com.inventory.controller;

import com.inventory.model.Product;
import com.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProductRepository productRepository;

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        List<Product> all = productRepository.findAll();
        BigDecimal totalValue = all.stream()
                .map(p -> p.getQuantity().multiply(p.getPricePerUnit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long lowStockCount = all.stream().filter(Product::isLowStock).count();

        return Map.of(
                "totalProducts", all.size(),
                "lowStockCount", lowStockCount,
                "totalStockValue", totalValue,
                "categories", all.stream().map(Product::getCategory).distinct().count()
        );
    }
}
