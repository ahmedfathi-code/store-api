package com.springtest.product_store;

import com.jayway.jsonpath.JsonPath;
import com.springtest.product_store.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A replayed (already used) refresh token means someone else has it: every session of the
// user is revoked, including the tokens the thief obtained with it
class RefreshReuseIT extends AbstractIntegrationTest {

    private static final String USER = "user@example.com";

    private record Tokens(String access, String refresh) {
    }

    @BeforeEach
    void setUpUser() {
        createUser(USER, Role.ROLE_USER);
    }

    private static Tokens tokens(String body) {
        return new Tokens(JsonPath.read(body, "$.token"), JsonPath.read(body, "$.refreshToken"));
    }

    private Tokens login() throws Exception {
        return tokens(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(USER, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    // USER token on a product write: 403 while valid, 401 once revoked
    private ResultActions use(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    @Test
    void replayingAUsedRefreshTokenRevokesEverySessionOfTheUser() throws Exception {
        Tokens stolen = login();
        Tokens otherDevice = login();

        // the thief uses the stolen refresh token first and gets a fresh pair
        Tokens thief = tokens(refresh(stolen.refresh())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        use(thief.access()).andExpect(status().isForbidden());

        // the real user (or the thief again) presents the same, already used token
        refresh(stolen.refresh())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));

        // everything issued so far is dead: the thief's new tokens and every other session
        use(thief.access()).andExpect(status().isUnauthorized());
        refresh(thief.refresh()).andExpect(status().isUnauthorized());
        use(otherDevice.access()).andExpect(status().isUnauthorized());
        refresh(otherDevice.refresh()).andExpect(status().isUnauthorized());

        // the real user logs in again and is back in control
        Tokens fresh = login();
        use(fresh.access()).andExpect(status().isForbidden());
        refresh(fresh.refresh()).andExpect(status().isOk());
    }

    // Logout deletes the refresh token: replaying it is just an invalid token, not reuse
    @Test
    void replayingALoggedOutRefreshTokenDoesNotRevokeOtherSessions() throws Exception {
        Tokens loggedOut = login();
        Tokens otherDevice = login();
        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + loggedOut.access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(loggedOut.refresh())))
                .andExpect(status().isNoContent());

        refresh(loggedOut.refresh()).andExpect(status().isUnauthorized());

        use(otherDevice.access()).andExpect(status().isForbidden());
        refresh(otherDevice.refresh()).andExpect(status().isOk());
    }

    @Test
    void unknownRefreshTokenDoesNotRevokeAnything() throws Exception {
        Tokens session = login();

        refresh("made-up-token").andExpect(status().isUnauthorized());

        use(session.access()).andExpect(status().isForbidden());
        assertThat(redis.keys("sessions-valid-after:*")).isEmpty();
    }

    @Test
    void usedMarkerIsHashedAndLivesAsLongAsTheTokenCould() throws Exception {
        Tokens session = login();
        refresh(session.refresh()).andExpect(status().isOk());

        Set<String> used = redis.keys("refresh-used:*");
        assertThat(used).hasSize(1);
        String key = used.iterator().next();
        assertThat(key).doesNotContain(session.refresh());
        assertThat(redis.getExpire(key)).isBetween(604_000L, 604_800L);
    }
}
