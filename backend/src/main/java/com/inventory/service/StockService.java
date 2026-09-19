package com.inventory.service;

import com.inventory.dto.ProductDto;
import com.inventory.dto.StockTransactionRequest;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;
    private final StockTransactionRepository transactionRepository;
    private final ProductService productService;

    @Transactional
    public ProductDto recordTransaction(StockTransactionRequest request) {
        Product product = productRepository.findByNameIgnoreCase(request.getProductName().trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product '" + request.getProductName() + "' not found. Add it first."));

        StockTransaction.Type type = parseType(request.getType());
        applyMovement(product, type, request.getQuantity());

        StockTransaction txn = new StockTransaction();
        txn.setProduct(product);
        txn.setType(type);
        txn.setQuantity(request.getQuantity());
        txn.setUnit(request.getUnit() != null && !request.getUnit().isBlank() ? request.getUnit() : product.getUnit());
        txn.setPricePerUnit(request.getPricePerUnit() != null ? request.getPricePerUnit() : product.getPricePerUnit());
        txn.setSource(StockTransaction.Source.MANUAL);
        txn.setNote(request.getNote());
        transactionRepository.save(txn);

        Product saved = productRepository.save(product);
        return productService.toDto(saved);
    }

    /** Used by the voice command parser so it can carry the raw transcript + language for audit history. */
    @Transactional
    public Product applyVoiceMovement(Product product, StockTransaction.Type type, BigDecimal quantity,
                                       String unit, BigDecimal pricePerUnit, String transcript, String language) {
        applyMovement(product, type, quantity);

        StockTransaction txn = new StockTransaction();
        txn.setProduct(product);
        txn.setType(type);
        txn.setQuantity(quantity);
        txn.setUnit(unit != null ? unit : product.getUnit());
        txn.setPricePerUnit(pricePerUnit != null ? pricePerUnit : product.getPricePerUnit());
        txn.setSource(StockTransaction.Source.VOICE);
        txn.setRawTranscript(transcript);
        txn.setLanguage(language);
        transactionRepository.save(txn);

        return productRepository.save(product);
    }

    private void applyMovement(Product product, StockTransaction.Type type, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (type == StockTransaction.Type.IN) {
            product.setQuantity(product.getQuantity().add(quantity));
        } else {
            BigDecimal newQty = product.getQuantity().subtract(quantity);
            if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Not enough stock of '" + product.getName() + "'. Available: "
                                + product.getQuantity() + " " + product.getUnit());
            }
            product.setQuantity(newQty);
        }
    }

    private StockTransaction.Type parseType(String type) {
        try {
            return StockTransaction.Type.valueOf(type.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Type must be IN or OUT");
        }
    }

    public List<Map<String, Object>> getHistory(Long productId) {
        return transactionRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    public List<Map<String, Object>> getRecentActivity() {
        return transactionRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    private Map<String, Object> toHistoryEntry(StockTransaction t) {
        return Map.ofEntries(
                Map.entry("id", t.getId()),
                Map.entry("productId", t.getProduct().getId()),
                Map.entry("productName", t.getProduct().getName()),
                Map.entry("type", t.getType().name()),
                Map.entry("quantity", t.getQuantity()),
                Map.entry("unit", t.getUnit()),
                Map.entry("pricePerUnit", t.getPricePerUnit() == null ? BigDecimal.ZERO : t.getPricePerUnit()),
                Map.entry("source", t.getSource().name()),
                Map.entry("note", t.getNote() == null ? "" : t.getNote()),
                Map.entry("createdAt", t.getCreatedAt().toString())
        );
    }
}
