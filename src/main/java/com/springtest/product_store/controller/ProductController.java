package com.springtest.product_store.controller;



import com.springtest.product_store.config.OpenApiConfig;
import com.springtest.product_store.dto.ErrorResponse;
import com.springtest.product_store.dto.ProductRequest;
import com.springtest.product_store.dto.ProductResponseDto;
import com.springtest.product_store.entity.Product;
import com.springtest.product_store.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@Tag(name = "products", description = "Product catalog: reads are public, writes need an ADMIN token")
public class ProductController {

    @Autowired
    private ProductService productService;

    // ✅ Create
    @PostMapping
    @Operation(summary = "Create a product (ADMIN)")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Invalid body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing, invalid, expired or revoked token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Authenticated, but not ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest request) {
        Product created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ✅ Get All (Pagination + Sorting)
    @GetMapping
    @Operation(summary = "List products", description = "Paginated and sortable. Invalid paging or sort values return 400.")
    @ApiResponse(responseCode = "200", description = "A page of products")
    @ApiResponse(responseCode = "400", description = "Invalid page, size, sortBy or direction",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Page<ProductResponseDto>> getAllProducts(
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0")    @Min(value = 0, message = "page must be 0 or greater") int page,
            @Parameter(description = "Page size, 1 to 100")
            @RequestParam(defaultValue = "10")   @Min(value = 1, message = "size must be 1 or greater")
            @Max(value = 100, message = "size must be at most 100") int size,
            @Parameter(description = "Product field to sort by",
                    schema = @Schema(defaultValue = "id", allowableValues = {"id", "name", "price", "category", "stock"}))
            // Only plain property names reach Spring Data: anything else (spaces, symbols, very
            // long values) is a 400 here instead of Spring Data's "unsafe sort expression" 500,
            // and unknown names that do pass are safe to echo in the error message
            @RequestParam(defaultValue = "id")   @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.]{0,49}",
                    message = "Invalid sort property") String sortBy,
            @Parameter(description = "Sort direction, case-insensitive")
            @RequestParam(defaultValue = "asc")  @Pattern(regexp = "asc|desc", flags = Pattern.Flag.CASE_INSENSITIVE,
                    message = "direction must be 'asc' or 'desc'") String direction) {
        return ResponseEntity.ok(productService.getAllProducts(page, size, sortBy, direction));
    }

    // ✅ Get By ID
    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    @ApiResponse(responseCode = "200", description = "The product")
    @ApiResponse(responseCode = "400", description = "Non-numeric id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No product with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ProductResponseDto> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    // ✅ Search by Name
    @GetMapping("/search/name")
    @Operation(summary = "Search products by name", description = "Case-insensitive \"contains\" match, paginated.")
    @ApiResponse(responseCode = "200", description = "A page of matching products")
    @ApiResponse(responseCode = "400", description = "Invalid page or size",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Page<Product>> searchByName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0")  @Min(value = 0, message = "page must be 0 or greater") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be 1 or greater")
            @Max(value = 100, message = "size must be at most 100") int size) {

        return ResponseEntity.ok(productService.searchByName(name, page, size));
    }

    // ✅ Search by Category
    @GetMapping("/search/category")
    @Operation(summary = "Search products by category", description = "Case-insensitive exact match, paginated.")
    @ApiResponse(responseCode = "200", description = "A page of matching products")
    @ApiResponse(responseCode = "400", description = "Invalid page or size",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Page<Product>> searchByCategory(
            @RequestParam String category,
            @RequestParam(defaultValue = "0")  @Min(value = 0, message = "page must be 0 or greater") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be 1 or greater")
            @Max(value = 100, message = "size must be at most 100") int size) {

        return ResponseEntity.ok(productService.searchByCategory(category, page, size));
    }

    // ✅ Update
    @PutMapping("/{id}")
    @Operation(summary = "Replace a product (ADMIN)")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "400", description = "Invalid body or non-numeric id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing, invalid, expired or revoked token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Authenticated, but not ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No product with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {

        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    // ✅ Delete
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product (ADMIN)")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "Deleted", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing, invalid, expired or revoked token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Authenticated, but not ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No product with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
