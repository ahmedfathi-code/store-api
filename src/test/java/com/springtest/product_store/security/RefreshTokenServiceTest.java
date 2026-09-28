package com.springtest.product_store.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Duration VALIDITY = Duration.ofDays(7);
    private static final String EMAIL = "user@example.com";

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(redis, VALIDITY);
    }

    @Test
    void issueStoresHashedTokenMappedToEmailAndIssueTimeWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        long before = System.currentTimeMillis();

        String token = service.issue(EMAIL);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(key.capture(), value.capture(), eq(VALIDITY));
        assertThat(key.getValue()).isEqualTo("refresh:" + TokenHasher.sha256(token));
        assertThat(key.getValue()).doesNotContain(token);
        RefreshTokenService.Consumed stored = RefreshTokenService.parse(value.getValue());
        assertThat(stored.email()).isEqualTo(EMAIL);
        assertThat(stored.issuedAtMillis()).isBetween(before, System.currentTimeMillis());
    }

    @Test
    void issueReturnsUniqueUrlSafe256BitTokens() {
        when(redis.opsForValue()).thenReturn(valueOps);

        String first = service.issue(EMAIL);
        String second = service.issue(EMAIL);

        assertThat(first).isNotEqualTo(second);
        // 32 random bytes, Base64 URL-safe without padding = 43 chars
        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void consumeReturnsEmailAndIssueTimeAndDeletesTokenAtomically() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("refresh:" + TokenHasher.sha256("abc"))).thenReturn(EMAIL + "|1700000000123");

        assertThat(service.consume("abc")).contains(new RefreshTokenService.Consumed(EMAIL, 1700000000123L));
    }

    // Tokens stored before issue times were recorded hold only the email
    @Test
    void oldFormatValueIsTreatedAsIssuedAtZero() {
        assertThat(RefreshTokenService.parse(EMAIL)).isEqualTo(new RefreshTokenService.Consumed(EMAIL, 0L));
    }

    @Test
    void emailContainingSeparatorIsParsedInBothFormats() {
        String odd = "a|b@example.com";
        assertThat(RefreshTokenService.parse(odd + "|42")).isEqualTo(new RefreshTokenService.Consumed(odd, 42L));
        assertThat(RefreshTokenService.parse(odd)).isEqualTo(new RefreshTokenService.Consumed(odd, 0L));
    }

    @Test
    void consumeReturnsEmptyForUnknownOrAlreadyUsedToken() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("refresh:" + TokenHasher.sha256("used"))).thenReturn(null);

        assertThat(service.consume("used")).isEmpty();
    }

    @Test
    void revokeDeletesHashedKey() {
        service.revoke("abc");

        verify(redis).delete("refresh:" + TokenHasher.sha256("abc"));
    }
}
