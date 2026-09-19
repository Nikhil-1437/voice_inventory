package com.inventory.repository;

import com.inventory.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByNameIgnoreCase(String name);

    Optional<Product> findByLocalNameIgnoreCase(String localName);

    List<Product> findByNameContainingIgnoreCaseOrLocalNameContainingIgnoreCase(String name, String localName);

    @Query("SELECT p FROM Product p WHERE p.quantity <= p.lowStockThreshold ORDER BY p.quantity ASC")
    List<Product> findLowStockProducts();

    List<Product> findByCategoryIgnoreCase(String category);
}
