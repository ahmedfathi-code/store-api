package com.springtest.product_store;

import com.jayway.jsonpath.JsonPath;
import com.springtest.product_store.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// POST /api/auth/change-password, and that it logs out every other session
class PasswordChangeIT extends AbstractIntegrationTest {

    private static final String USER = "user@example.com";
    private static final String NEW_PASSWORD = "new-Passw0rd!";

    private record Tokens(String access, String refresh) {
    }

    private Tokens login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.token"), JsonPath.read(body, "$.refreshToken"));
    }

    private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
    }

    // A USER token on a product write: 403 while the token is valid, 401 once it's revoked
    private ResultActions useAccessToken(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private ResultActions useRefreshToken(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    @Test
    void changingPasswordLogsOutEveryOtherSessionAndKeepsTheCallerLoggedIn() throws Exception {
        createUser(USER, Role.ROLE_USER);
        Tokens phone = login(USER, PASSWORD);
        Tokens laptop = login(USER, PASSWORD);

        String body = changePassword(phone.access(), PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        Tokens fresh = new Tokens(JsonPath.read(body, "$.token"), JsonPath.read(body, "$.refreshToken"));

        // every token issued before the change is revoked, including the caller's own old ones
        useAccessToken(phone.access()).andExpect(status().isUnauthorized());
        useAccessToken(laptop.access()).andExpect(status().isUnauthorized());
        useRefreshToken(phone.refresh()).andExpect(status().isUnauthorized());
        useRefreshToken(laptop.refresh()).andExpect(status().isUnauthorized());

        // the tokens returned by the change work
        useAccessToken(fresh.access()).andExpect(status().isForbidden());
        useRefreshToken(fresh.refresh()).andExpect(status().isOk());

        // old password refused, new one accepted, and new logins work normally
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(USER, PASSWORD)))
                .andExpect(status().isUnauthorized());
        Tokens afterwards = login(USER, NEW_PASSWORD);
        useAccessToken(afterwards.access()).andExpect(status().isForbidden());

        // the revocation marker expires with the longest token lifetime
        assertThat(redis.getExpire("sessions-valid-after:" + USER)).isBetween(604_000L, 604_800L);
    }

    @Test
    void wrongCurrentPasswordIsRejectedAndChangesNothing() throws Exception {
        createUser(USER, Role.ROLE_USER);
        Tokens session = login(USER, PASSWORD);

        changePassword(session.access(), "not-my-password", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));

        useAccessToken(session.access()).andExpect(status().isForbidden());
        login(USER, PASSWORD);
    }

    @Test
    void newPasswordMustDifferFromTheCurrentOne() throws Exception {
        createUser(USER, Role.ROLE_USER);
        Tokens session = login(USER, PASSWORD);

        changePassword(session.access(), PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password must be different from the current one"));
        useAccessToken(session.access()).andExpect(status().isForbidden());
    }

    @Test
    void minimumLengthDependsOnRole() throws Exception {
        createUser(USER, Role.ROLE_USER);
        createUser("admin@example.com", Role.ROLE_ADMIN);
        Tokens user = login(USER, PASSWORD);
        Tokens admin = login("admin@example.com", PASSWORD);

        changePassword(user.access(), PASSWORD, "12345")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password must be at least 6 characters"));
        changePassword(user.access(), PASSWORD, "123456").andExpect(status().isOk());

        changePassword(admin.access(), PASSWORD, "elevenchars")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password must be at least 12 characters"));
        changePassword(admin.access(), PASSWORD, "twelve-chars").andExpect(status().isOk());
    }

    @Test
    void missingFieldsAreValidationErrors() throws Exception {
        createUser(USER, Role.ROLE_USER);
        Tokens session = login(USER, PASSWORD);

        mockMvc.perform(post("/api/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password is required"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"bcdefgh\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void messagesAreTranslated() throws Exception {
        createUser(USER, Role.ROLE_USER);
        Tokens session = login(USER, PASSWORD);

        mockMvc.perform(post("/api/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.access())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"123\"}".formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("الباسورد الجديد لازم يكون 6 حروف على الأقل"));
    }
}
