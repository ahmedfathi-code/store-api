package com.springtest.product_store.repository;

import com.springtest.product_store.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // البحث باسم المنتج (Pagination)
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // البحث بالكاتيجوري (Pagination)
    Page<Product> findByCategoryIgnoreCase(String category, Pageable pageable);
}