package com.springtest.product_store;

import com.jayway.jsonpath.JsonPath;
import com.springtest.product_store.config.AdminSeeder;
import com.springtest.product_store.entity.User;
import com.springtest.product_store.model.Role;
import com.springtest.product_store.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Own context (not AbstractIntegrationTest, which wipes users before each test):
// the admin must exist right after startup, seeded from the admin properties
@SpringBootTest(properties = {
        "app.admin.email=" + AdminSeedIT.EMAIL,
        "app.admin.password=" + AdminSeedIT.PASSWORD
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminSeedIT {

    static final String EMAIL = "seeded-admin@example.com";
    static final String PASSWORD = "seeded-admin-pass-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminSeeder adminSeeder;

    @Test
    void seededAdminExistsCanLogInAndWriteProducts() throws Exception {
        User admin = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(admin.getPassword()).isNotEqualTo(PASSWORD);

        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Seeded\",\"price\":1.5,\"category\":\"office\",\"stock\":3}"))
                .andExpect(status().isCreated());
    }

    @Test
    void runningAgainDoesNotDuplicateOrChangeThePassword() {
        String hashBefore = userRepository.findByEmail(EMAIL).orElseThrow().getPassword();
        long usersBefore = userRepository.count();

        adminSeeder.run(null);

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getPassword()).isEqualTo(hashBefore);
    }
}
