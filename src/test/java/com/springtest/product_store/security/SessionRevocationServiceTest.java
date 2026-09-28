package com.springtest.product_store.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionRevocationServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String KEY = "sessions-valid-after:" + EMAIL;
    private static final long NOW = 1_800_000_000_000L;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private SessionRevocationService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
        service = new SessionRevocationService(redis, Duration.ofDays(7), fixed);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void revokeStoresNowWithTheLongestTokenLifetimeAsTtl() {
        assertThat(service.revokeAllSessions(EMAIL)).isEqualTo(NOW);

        verify(valueOps).set(KEY, Long.toString(NOW), Duration.ofDays(7));
    }

    @Test
    void tokensIssuedBeforeRevocationAreRevoked() {
        when(valueOps.get(KEY)).thenReturn(Long.toString(NOW));

        assertThat(service.isRevoked(EMAIL, NOW - 1)).isTrue();
    }

    @Test
    void tokensIssuedAtOrAfterRevocationAreValid() {
        when(valueOps.get(KEY)).thenReturn(Long.toString(NOW));

        assertThat(service.isRevoked(EMAIL, NOW)).isFalse();
        assertThat(service.isRevoked(EMAIL, NOW + 1)).isFalse();
    }

    @Test
    void nothingIsRevokedWithoutARevocation() {
        when(valueOps.get(KEY)).thenReturn(null);

        assertThat(service.isRevoked(EMAIL, 0L)).isFalse();
    }
}
