package com.springtest.product_store;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// OpenAPI spec and Swagger UI are public and describe the whole API
class OpenApiIT extends AbstractIntegrationTest {

    @Test
    void specIsPublicAndListsAllEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Store API"))
                .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products'].post").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/products/search/name'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products/search/category'].get").exists());
    }

    @Test
    void specDeclaresJwtBearerScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    void paginationLimitsAreDocumented() throws Exception {
        String size = "$.paths['/api/products'].get.parameters[?(@.name == 'size')].schema";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(size + ".minimum").value(1))
                .andExpect(jsonPath(size + ".maximum").value(100))
                .andExpect(jsonPath("$.paths['/api/products'].get.parameters[?(@.name == 'direction')].schema.pattern")
                        .value("asc|desc"));
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
