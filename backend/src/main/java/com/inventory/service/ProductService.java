package com.inventory.service;

import com.inventory.dto.ProductDto;
import com.inventory.dto.ProductRequest;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.model.Product;
import com.inventory.model.StockTransaction;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.StockTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final StockTransactionRepository transactionRepository;

    public List<ProductDto> getAllProducts() {
        return productRepository.findAll().stream().map(this::toDto).toList();
    }

    public ProductDto getProduct(Long id) {
        return toDto(findProductOrThrow(id));
    }

    public List<ProductDto> searchProducts(String query) {
        return productRepository
                .findByNameContainingIgnoreCaseOrLocalNameContainingIgnoreCase(query, query)
                .stream().map(this::toDto).toList();
    }

    public List<ProductDto> getLowStockProducts() {
        return productRepository.findLowStockProducts().stream().map(this::toDto).toList();
    }

    @Transactional
    public ProductDto createProduct(ProductRequest request) {
        productRepository.findByNameIgnoreCase(request.getName()).ifPresent(p -> {
            throw new IllegalArgumentException("A product named '" + request.getName() + "' already exists");
        });

        Product product = new Product();
        applyRequest(product, request);

        Product saved = productRepository.save(product);

        if (saved.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            StockTransaction openingStock = new StockTransaction();
            openingStock.setProduct(saved);
            openingStock.setType(StockTransaction.Type.IN);
            openingStock.setQuantity(saved.getQuantity());
            openingStock.setUnit(saved.getUnit());
            openingStock.setPricePerUnit(saved.getPricePerUnit());
            openingStock.setSource(StockTransaction.Source.MANUAL);
            openingStock.setNote("Opening stock");
            transactionRepository.save(openingStock);
        }

        return toDto(saved);
    }

    @Transactional
    public ProductDto updateProduct(Long id, ProductRequest request) {
        Product product = findProductOrThrow(id);
        applyRequest(product, request);
        return toDto(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProductOrThrow(id);
        productRepository.delete(product);
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName().trim());
        product.setLocalName(request.getLocalName());
        product.setCategory(request.getCategory() == null || request.getCategory().isBlank()
                ? "General" : request.getCategory());
        product.setUnit(request.getUnit().trim().toLowerCase());
        if (request.getQuantity() != null) product.setQuantity(request.getQuantity());
        if (request.getPricePerUnit() != null) product.setPricePerUnit(request.getPricePerUnit());
        if (request.getLowStockThreshold() != null) product.setLowStockThreshold(request.getLowStockThreshold());
    }

    protected Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    public ProductDto toDto(Product p) {
        return ProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .localName(p.getLocalName())
                .category(p.getCategory())
                .unit(p.getUnit())
                .quantity(p.getQuantity())
                .pricePerUnit(p.getPricePerUnit())
                .lowStockThreshold(p.getLowStockThreshold())
                .lowStock(p.isLowStock())
                .build();
    }
}
