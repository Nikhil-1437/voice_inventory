package com.inventory.repository;

import com.inventory.model.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    @Query("SELECT t FROM StockTransaction t JOIN FETCH t.product WHERE t.product.id = :productId ORDER BY t.createdAt DESC")
    List<StockTransaction> findByProductIdOrderByCreatedAtDesc(Long productId);

    @Query("SELECT t FROM StockTransaction t JOIN FETCH t.product ORDER BY t.createdAt DESC")
    List<StockTransaction> findTop20ByOrderByCreatedAtDesc();
}