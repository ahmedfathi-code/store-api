package com.springtest.product_store;

import com.springtest.product_store.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// OPENAPI_ENABLED=false (these two properties) removes the spec and Swagger UI entirely
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class OpenApiDisabledIT extends AbstractIntegrationTest {

    @Test
    void specAndUiAreNotServed() throws Exception {
        // Authenticated, so a 404 proves the routes are gone (not just blocked by security)
        String token = bearer(createUser("user@example.com", Role.ROLE_USER));

        mockMvc.perform(get("/v3/api-docs").header("Authorization", token)).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html").header("Authorization", token)).andExpect(status().isNotFound());
    }
}
