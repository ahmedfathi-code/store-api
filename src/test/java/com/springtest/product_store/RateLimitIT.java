package com.springtest.product_store;

import com.springtest.product_store.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Failed-password rate limits (defaults: 5 per IP+email, 30 per IP, 15-minute window)
class RateLimitIT extends AbstractIntegrationTest {

    private static final String EMAIL = "user@example.com";
    private static final String ATTACKER_IP = "203.0.113.7";

    @BeforeEach
    void setUpUser() {
        createUser(EMAIL, Role.ROLE_USER);
    }

    private ResultActions login(String ip, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private void failLogins(String ip, String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            login(ip, email, "wrong-" + i).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void sixthAttemptIsBlockedEvenWithTheRightPassword() throws Exception {
        failLogins(ATTACKER_IP, EMAIL, 5);

        login(ATTACKER_IP, EMAIL, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER,
                        matchesPattern("\\d+")))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(startsWith("Too many attempts, try again in")));
    }

    @Test
    void retryAfterIsWithinTheWindow() throws Exception {
        failLogins(ATTACKER_IP, EMAIL, 5);

        String retryAfter = login(ATTACKER_IP, EMAIL, "wrong")
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(Integer.parseInt(retryAfter), allOf(greaterThan(0), lessThanOrEqualTo(900)));
    }

    // The victim (or anyone from another address) isn't locked out by an attacker's failures
    @Test
    void anotherIpCanStillLogInToTheSameAccount() throws Exception {
        failLogins(ATTACKER_IP, EMAIL, 5);

        login("198.51.100.20", EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsTheAccountCounter() throws Exception {
        failLogins(ATTACKER_IP, EMAIL, 4);
        login(ATTACKER_IP, EMAIL, PASSWORD).andExpect(status().isOk());

        // 4 more failures would have been blocked without the reset
        failLogins(ATTACKER_IP, EMAIL, 4);
        login(ATTACKER_IP, EMAIL, PASSWORD).andExpect(status().isOk());
    }

    // One machine trying many accounts (credential stuffing)
    @Test
    void ipIsBlockedAfterThirtyFailuresAcrossAccounts() throws Exception {
        for (int i = 0; i < 30; i++) {
            login(ATTACKER_IP, "victim" + i + "@example.com", "guess").andExpect(status().isUnauthorized());
        }

        login(ATTACKER_IP, EMAIL, PASSWORD).andExpect(status().isTooManyRequests());
        login("198.51.100.20", EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void messageIsTranslated() throws Exception {
        failLogins(ATTACKER_IP, EMAIL, 5);

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(ATTACKER_IP);
                            return request;
                        })
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"x\"}".formatted(EMAIL)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(startsWith("محاولات كتير")));
    }
}
