package com.springtest.product_store;

import com.springtest.product_store.model.Role;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Only /actuator/health is exposed, publicly and without component details
class HealthEndpointIT extends AbstractIntegrationTest {

    @Test
    void healthIsPublicAndUpWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        String admin = bearer(createUser("admin@example.com", Role.ROLE_ADMIN));

        for (String endpoint : new String[]{"/actuator/env", "/actuator/beans", "/actuator/configprops"}) {
            mockMvc.perform(get(endpoint).header("Authorization", admin))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void otherActuatorEndpointsRequireAuthenticationFirst() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
