package com.springtest.product_store.controller;



import com.springtest.product_store.dto.ProductRequest;
import com.springtest.product_store.dto.ProductResponseDto;
import com.springtest.product_store.entity.Product;
import com.springtest.product_store.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    // ✅ Create
    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest request) {
        Product created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ✅ Get All (Pagination + Sorting)
    @GetMapping
    public ResponseEntity<Page<ProductResponseDto>> getAllProducts(
            @RequestParam(defaultValue = "0")    @Min(value = 0, message = "page must be 0 or greater") int page,
            @RequestParam(defaultValue = "10")   @Min(value = 1, message = "size must be 1 or greater") int size,
            @RequestParam(defaultValue = "id")   String sortBy,
            @RequestParam(defaultValue = "asc")  String direction) {
        return ResponseEntity.ok(productService.getAllProducts(page, size, sortBy, direction));
    }

    // ✅ Get By ID
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    // ✅ Search by Name
    @GetMapping("/search/name")
    public ResponseEntity<Page<Product>> searchByName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0")  @Min(value = 0, message = "page must be 0 or greater") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be 1 or greater") int size) {

        return ResponseEntity.ok(productService.searchByName(name, page, size));
    }

    // ✅ Search by Category
    @GetMapping("/search/category")
    public ResponseEntity<Page<Product>> searchByCategory(
            @RequestParam String category,
            @RequestParam(defaultValue = "0")  @Min(value = 0, message = "page must be 0 or greater") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be 1 or greater") int size) {

        return ResponseEntity.ok(productService.searchByCategory(category, page, size));
    }

    // ✅ Update
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {

        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    // ✅ Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}