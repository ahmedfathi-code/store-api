package com.springtest.product_store.service;

import com.springtest.product_store.dto.ProductRequest;
import com.springtest.product_store.dto.ProductResponseDto;
import com.springtest.product_store.entity.Product;
import com.springtest.product_store.exception.ResourceNotFoundException;
import com.springtest.product_store.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {
    @Autowired
    private ProductRepository productRepository;

    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        return productRepository.save(product);

    }

    // Used by GET /api/products: builds the page request and maps to DTOs
    public Page<ProductResponseDto> getAllProducts(int page , int size , String sortBy , String direction) {
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return productRepository.findAll(pageable).map(this::toDto);
    }


    // Single lookup (and 404 message) for GET, PUT and DELETE by id
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    // ✅ Search by Name (Pagination)
    public Page<Product> searchByName(String name, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    // ✅ Search by Category (Pagination)
    public Page<Product> searchByCategory(String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByCategoryIgnoreCase(category, pageable);
    }

    // ✅ Update
    public Product updateProduct(Long id, ProductRequest request) {
        Product product = getProductById(id);
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setStock(request.getStock());
        return productRepository.save(product);
    }

    // ✅ Delete
    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        productRepository.delete(product);
    }
    // بدل ما ترجع null أو Optional فاضي:
    // في ProductService — method بتحول Entity لـ DTO
    private ProductResponseDto toDto(Product product) {
        return new ProductResponseDto(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCategory()  // أو product.getCategory().getName() لو عندك relation
        );
    }

    public ProductResponseDto findById(Long id) {
        return toDto(getProductById(id));
    }








}
