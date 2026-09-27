package com.springtest.product_store;

import com.jayway.jsonpath.JsonPath;
import com.springtest.product_store.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Register / login / refresh / logout against real Postgres and Redis
class AuthTokensIT extends AbstractIntegrationTest {

    private static final String PRODUCT = """
            {"name":"Notebook","price":4.5,"category":"office","stock":20}
            """;

    private record Tokens(String access, String refresh) {
    }

    private Tokens login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.token"), JsonPath.read(body, "$.refreshToken"));
    }

    private String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
    }

    @Test
    void registerCreatesUserAndRejectsDuplicates() throws Exception {
        String body = "{\"email\":\"new@example.com\",\"password\":\"%s\"}".formatted(PASSWORD);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        assertThat(userRepository.findByEmail("new@example.com")).get()
                .satisfies(u -> {
                    assertThat(u.getRole()).isEqualTo(Role.ROLE_USER);
                    assertThat(u.getPassword()).isNotEqualTo(PASSWORD);
                });
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() throws Exception {
        createUser("user@example.com", Role.ROLE_USER);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        createUser("user@example.com", Role.ROLE_USER);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokensAndOldRefreshTokenCannotBeReused() throws Exception {
        createUser("admin@example.com", Role.ROLE_ADMIN);
        Tokens first = login("admin@example.com");

        String body = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(first.refresh())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        String newAccess = JsonPath.read(body, "$.token");
        String newRefresh = JsonPath.read(body, "$.refreshToken");
        assertThat(newRefresh).isNotEqualTo(first.refresh());

        // the new access token works on an ADMIN endpoint
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + newAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT))
                .andExpect(status().isCreated());

        // the consumed refresh token is single use
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(first.refresh())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithUnknownOrMissingTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("made-up")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutRevokesAccessTokenUntilExpiryAndRevokesRefreshToken() throws Exception {
        createUser("admin@example.com", Role.ROLE_ADMIN);
        Tokens tokens = login("admin@example.com");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + tokens.access())
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(tokens.refresh())))
                .andExpect(status().isNoContent());

        // revoked access token: rejected like an invalid token (403) on an ADMIN endpoint
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + tokens.access())
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT))
                .andExpect(status().isForbidden());
        // ...and cannot log out again
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isForbidden());
        // public reads still work
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isOk());
        // refresh token revoked too
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.refresh())))
                .andExpect(status().isUnauthorized());

        // blacklist entry expires with the token and stores only a hash
        Set<String> keys = redis.keys("blacklist:*");
        assertThat(keys).hasSize(1);
        String key = keys.iterator().next();
        assertThat(key).doesNotContain(tokens.access());
        assertThat(redis.getExpire(key)).isBetween(1L, 900L);
    }

    @Test
    void logoutWithoutBodyStillRevokesAccessToken() throws Exception {
        createUser("admin@example.com", Role.ROLE_ADMIN);
        Tokens tokens = login("admin@example.com");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + tokens.access())
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT))
                .andExpect(status().isForbidden());
        // refresh token was not sent, so it still works
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.refresh())))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
    }

    @Test
    void refreshTokensAreStoredHashed() throws Exception {
        createUser("user@example.com", Role.ROLE_USER);
        Tokens tokens = login("user@example.com");

        Set<String> keys = redis.keys("refresh:*");
        assertThat(keys).hasSize(1);
        assertThat(keys.iterator().next()).doesNotContain(tokens.refresh());
        assertThat(redis.getExpire(keys.iterator().next())).isBetween(604_000L, 604_800L);
    }
}
