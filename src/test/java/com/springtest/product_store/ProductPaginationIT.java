package com.springtest.product_store;

import com.springtest.product_store.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Pagination contract of GET /api/products: page, size, sortBy, direction
class ProductPaginationIT extends AbstractIntegrationTest {

    private static final int TOTAL = 25;

    private List<Long> idsInInsertOrder;

    // 25 products: "Product 01".."Product 25"; prices are a shuffled permutation
    // of 1..25 so sorting by price differs from sorting by id or name
    @BeforeEach
    void seedProducts() {
        idsInInsertOrder = new ArrayList<>();
        for (int i = 1; i <= TOTAL; i++) {
            double price = (i * 7 % TOTAL) + 1;
            String category = i % 2 == 0 ? "kitchen" : "office";
            Product saved = productRepository.save(
                    new Product(null, "Product %02d".formatted(i), price, category, i));
            idsInInsertOrder.add(saved.getId());
        }
    }

    private static List<Double> prices(int fromInclusive, int toInclusive, boolean descending) {
        List<Double> list = IntStream.rangeClosed(fromInclusive, toInclusive)
                .mapToObj(p -> (double) p).toList();
        return descending ? list.reversed() : list;
    }

    @Test
    void defaultsToFirstPageOfTenSortedByIdAscending() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(TOTAL))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.content[*].id").value(contains(
                        idsInInsertOrder.subList(0, 10).stream().map(Long::intValue).toArray())));
    }

    @Test
    void lastPageHoldsTheRemainder() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void customPageSize() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "1").param("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(7)))
                .andExpect(jsonPath("$.size").value(7))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.content[*].id").value(contains(
                        idsInInsertOrder.subList(7, 14).stream().map(Long::intValue).toArray())));
    }

    @Test
    void pageBeyondTheEndIsEmptyButKeepsTotals() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(TOTAL));
    }

    @Test
    void sortsByPriceDescending() throws Exception {
        mockMvc.perform(get("/api/products").param("sortBy", "price").param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].price").value(contains(prices(16, 25, true).toArray())));
    }

    @Test
    void sortsByNameAscending() throws Exception {
        mockMvc.perform(get("/api/products").param("sortBy", "name").param("direction", "asc").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("Product 01", "Product 02", "Product 03")));
    }

    @Test
    void sortingAppliesAcrossPages() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("sortBy", "price").param("direction", "asc")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].price").value(contains(prices(6, 10, false).toArray())));
    }

    @Test
    void responseUsesProductResponseDtoFields() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].name").value("Product 01"))
                .andExpect(jsonPath("$.content[0].price").exists())
                .andExpect(jsonPath("$.content[0].category").value("office"))
                .andExpect(jsonPath("$.content[0].stock").doesNotExist());
    }

    @Test
    void searchByNameIsPaginated() throws Exception {
        mockMvc.perform(get("/api/products/search/name").param("name", "PRODUCT 1").param("page", "0").param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)))
                // "Product 10".."Product 19" (case-insensitive contains)
                .andExpect(jsonPath("$.totalElements").value(10));
    }

    @Test
    void searchByCategoryIsPaginated() throws Exception {
        mockMvc.perform(get("/api/products/search/category").param("category", "OFFICE").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(13));
    }

    // --- invalid input (400) and missing resources (404) ---

    @Test
    void negativePageIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("page must be 0 or greater"));
    }

    @Test
    void zeroSizeIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size must be 1 or greater"));
    }

    @Test
    void nonNumericPageIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'page'"));
    }

    @Test
    void unknownSortPropertyIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products").param("sortBy", "foo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort property 'foo'"));
    }

    // Only identifier-like values are echoed back; anything else gets a generic message
    @Test
    void unsafeSortPropertyIsNotEchoed() throws Exception {
        String markup = "<script>alert(1)</script>";
        mockMvc.perform(get("/api/products").param("sortBy", markup))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort property"));

        String huge = "a".repeat(2000);
        mockMvc.perform(get("/api/products").param("sortBy", huge))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort property"));
    }

    @Test
    void sizeIsCappedAt100() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(TOTAL)));
        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size must be at most 100"));
        mockMvc.perform(get("/api/products/search/name").param("name", "x").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/products/search/category").param("category", "x").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownDirectionIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products").param("sortBy", "price").param("direction", "dsc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("direction must be 'asc' or 'desc'"));
    }

    @Test
    void directionIsCaseInsensitive() throws Exception {
        mockMvc.perform(get("/api/products").param("sortBy", "price").param("direction", "DESC").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].price").value(contains(25.0, 24.0)));
    }

    @Test
    void searchValidatesPaging() throws Exception {
        mockMvc.perform(get("/api/products/search/name").param("name", "x").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
