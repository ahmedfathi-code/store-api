package com.springtest.product_store.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "unit-test-jwt-secret-key-at-least-32-chars";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "SECRET", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "EXPIRATION", Duration.ofMinutes(15));
    }

    private static Claims claims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Same user, same second: tokens must still differ, or revoking one revokes the other
    @Test
    void tokensForTheSameUserInTheSameSecondAreDistinct() {
        String first = jwtUtil.generateToken("user@example.com");
        String second = jwtUtil.generateToken("user@example.com");

        assertThat(first).isNotEqualTo(second);
        assertThat(claims(first).getId()).isNotBlank().isNotEqualTo(claims(second).getId());
    }

    @Test
    void tokenStillRoundTrips() {
        String token = jwtUtil.generateToken("user@example.com");

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@example.com");
        assertThat(jwtUtil.getRemainingValidity(token))
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void tokenSignedWithAnotherKeyIsInvalid() {
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "SECRET", "another-unit-test-secret-key-32-chars-min");
        ReflectionTestUtils.setField(other, "EXPIRATION", Duration.ofMinutes(15));

        assertThat(jwtUtil.isTokenValid(other.generateToken("user@example.com"))).isFalse();
    }
}
