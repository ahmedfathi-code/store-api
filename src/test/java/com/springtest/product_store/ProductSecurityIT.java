package com.springtest.product_store;

import com.springtest.product_store.entity.Product;
import com.springtest.product_store.entity.User;
import com.springtest.product_store.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Role rules: product writes are ADMIN-only, reads are public
class ProductSecurityIT extends AbstractIntegrationTest {

    private static final String BODY = """
            {"name":"Notebook","price":4.5,"category":"office","stock":20}
            """;

    private User admin;
    private User user;
    private Product existing;

    @BeforeEach
    void setUpUsersAndProduct() {
        admin = createUser("admin@example.com", Role.ROLE_ADMIN);
        user = createUser("user@example.com", Role.ROLE_USER);
        existing = productRepository.save(new Product(null, "Pen", 2.5, "office", 10));
    }

    // --- USER is forbidden on ADMIN endpoints ---

    @Test
    void userCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        assertThat(productRepository.count()).isEqualTo(1);
    }

    @Test
    void userCannotUpdateProduct() throws Exception {
        mockMvc.perform(put("/api/products/{id}", existing.getId()).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        assertThat(productRepository.findById(existing.getId())).get()
                .extracting(Product::getName).isEqualTo("Pen");
    }

    @Test
    void userCannotDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", existing.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        assertThat(productRepository.existsById(existing.getId())).isTrue();
    }

    // --- ADMIN is allowed ---

    @Test
    void adminCanCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Notebook"));
        assertThat(productRepository.count()).isEqualTo(2);
    }

    @Test
    void adminCanUpdateProduct() throws Exception {
        mockMvc.perform(put("/api/products/{id}", existing.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Notebook"))
                .andExpect(jsonPath("$.stock").value(20));
    }

    @Test
    void adminCanDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", existing.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        assertThat(productRepository.existsById(existing.getId())).isFalse();
    }

    @Test
    void adminGetsNotFoundForMissingProduct() throws Exception {
        mockMvc.perform(put("/api/products/{id}", 999_999).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/products/{id}", 999_999).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    // Non-numeric ids used to surface as 500
    @Test
    void nonNumericIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
        mockMvc.perform(put("/api/products/abc").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/products/abc").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminGetsBadRequestForInvalidBody() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","price":-1,"category":"","stock":-5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // --- anonymous and invalid tokens: 401 (RFC 6750), not 403 ---

    @Test
    void anonymousCannotWrite() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
        mockMvc.perform(put("/api/products/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/products/{id}", existing.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenCannotWrite() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer not.a.jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid, expired or revoked token"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        // header.payload of a real-looking JWT with a signature from a different key
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBleGFtcGxlLmNvbSJ9."
                + "c2lnbmVkLXdpdGgtYW5vdGhlci1rZXktMTIzNDU2Nzg5MDEyMzQ1Njc4OTA";
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + forged)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
        assertThat(productRepository.count()).isEqualTo(1);
    }

    @Test
    void validTokenOfDeletedUserIsRejected() throws Exception {
        String token = bearer(admin);
        userRepository.delete(admin);

        mockMvc.perform(post("/api/products").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
    }

    @Test
    void nonBearerAuthorizationHeaderIsAPlainChallenge() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", "Basic dXNlcjpwYXNz")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void readsArePublic() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/{id}", existing.getId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/search/name").param("name", "pen")).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/search/category").param("category", "office")).andExpect(status().isOk());
    }

    @Test
    void userCanStillRead() throws Exception {
        mockMvc.perform(get("/api/products").header("Authorization", bearer(user)))
                .andExpect(status().isOk());
    }
}
