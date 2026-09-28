package com.springtest.product_store.service;

import com.springtest.product_store.dto.ProductRequest;
import com.springtest.product_store.dto.ProductResponseDto;
import com.springtest.product_store.entity.Product;
import com.springtest.product_store.exception.ResourceNotFoundException;
import com.springtest.product_store.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private static Product product(Long id, String name, double price, String category, int stock) {
        return new Product(id, name, price, category, stock);
    }

    private static ProductRequest request(String name, double price, String category, int stock) {
        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setPrice(price);
        request.setCategory(category);
        request.setStock(stock);
        return request;
    }

    // --- create ---

    @Test
    void createProductMapsRequestFieldsAndSaves() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product toSave = inv.getArgument(0);
            return new Product(7L, toSave.getName(), toSave.getPrice(), toSave.getCategory(), toSave.getStock());
        });

        ProductResponseDto created = productService.createProduct(request("Pen", 2.5, "office", 10));

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue())
                .extracting(Product::getName, Product::getPrice, Product::getCategory, Product::getStock)
                .containsExactly("Pen", 2.5, "office", 10);
        // the response is the DTO of what was saved, including the generated id
        assertThat(created)
                .extracting(ProductResponseDto::getId, ProductResponseDto::getName, ProductResponseDto::getPrice,
                        ProductResponseDto::getCategory, ProductResponseDto::getStock)
                .containsExactly(7L, "Pen", 2.5, "office", 10);
    }

    // --- pagination and sorting ---

    @Test
    void getAllProductsBuildsAscendingSortByDefault() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        productService.getAllProducts(2, 5, "name", "asc");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("name").ascending());
    }

    @Test
    void getAllProductsBuildsDescendingSortCaseInsensitively() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        productService.getAllProducts(0, 10, "price", "DESC");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("price").descending());
    }

    @Test
    void getAllProductsKeepsPageMetadataAndMapsToDtos() {
        Pageable pageable = PageRequest.of(1, 2, Sort.by("id").ascending());
        Page<Product> page = new PageImpl<>(
                List.of(product(3L, "Pen", 2.5, "office", 10), product(4L, "Mug", 7.0, "kitchen", 3)),
                pageable, 6);
        when(productRepository.findAll(pageable)).thenReturn(page);

        Page<ProductResponseDto> result = productService.getAllProducts(1, 2, "id", "asc");

        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(ProductResponseDto::getId, ProductResponseDto::getName,
                        ProductResponseDto::getPrice, ProductResponseDto::getCategory, ProductResponseDto::getStock)
                .containsExactly(
                        tuple(3L, "Pen", 2.5, "office", 10),
                        tuple(4L, "Mug", 7.0, "kitchen", 3));
    }

    @Test
    void searchByNamePassesPageAndSizeWithoutSort() {
        when(productRepository.findByNameContainingIgnoreCase("pen", PageRequest.of(1, 3)))
                .thenReturn(Page.empty());

        productService.searchByName("pen", 1, 3);

        verify(productRepository).findByNameContainingIgnoreCase("pen", PageRequest.of(1, 3));
    }

    @Test
    void searchResultsAreMappedToDtos() {
        when(productRepository.findByCategoryIgnoreCase("office", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(product(5L, "Stapler", 3.0, "office", 8))));

        assertThat(productService.searchByCategory("office", 0, 10).getContent())
                .extracting(ProductResponseDto::getId, ProductResponseDto::getStock)
                .containsExactly(tuple(5L, 8));
    }

    @Test
    void searchByCategoryPassesPageAndSizeWithoutSort() {
        when(productRepository.findByCategoryIgnoreCase("office", PageRequest.of(0, 10)))
                .thenReturn(Page.empty());

        productService.searchByCategory("office", 0, 10);

        verify(productRepository).findByCategoryIgnoreCase("office", PageRequest.of(0, 10));
    }

    // --- find by id ---

    @Test
    void findByIdReturnsDto() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "Pen", 2.5, "office", 10)));

        ProductResponseDto dto = productService.findById(1L);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Pen");
    }

    @Test
    void findByIdThrowsNotFoundForMissingProduct() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- update ---

    @Test
    void updateProductOverwritesAllFields() {
        Product existing = product(1L, "Pen", 2.5, "office", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        ProductResponseDto updated = productService.updateProduct(1L, request("Pencil", 1.0, "school", 50));

        assertThat(updated)
                .extracting(ProductResponseDto::getId, ProductResponseDto::getName, ProductResponseDto::getPrice,
                        ProductResponseDto::getCategory, ProductResponseDto::getStock)
                .containsExactly(1L, "Pencil", 1.0, "school", 50);
    }

    @Test
    void updateProductThrowsNotFoundAndDoesNotSave() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(99L, request("X", 1.0, "c", 1)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    // --- delete ---

    @Test
    void deleteProductDeletesExistingEntity() {
        Product existing = product(1L, "Pen", 2.5, "office", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

        productService.deleteProduct(1L);

        verify(productRepository).delete(existing);
    }

    @Test
    void deleteProductThrowsNotFoundAndDoesNotDelete() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).delete(any());
    }
}
