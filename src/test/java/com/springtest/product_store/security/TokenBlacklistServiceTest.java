package com.springtest.product_store.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    private static final String TOKEN = "header.payload.signature";
    private static final String KEY = "blacklist:" + TokenHasher.sha256(TOKEN);

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new TokenBlacklistService(redis);
    }

    @Test
    void blacklistStoresHashedTokenWithRemainingValidityAsTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        service.blacklist(TOKEN, Duration.ofMinutes(10));

        verify(valueOps).set(KEY, "1", Duration.ofMinutes(10));
    }

    @Test
    void blacklistNeverStoresTheRawToken() {
        assertThat(KEY).doesNotContain(TOKEN);
    }

    @Test
    void blacklistSkipsAlreadyExpiredToken() {
        service.blacklist(TOKEN, Duration.ZERO);

        verify(redis, never()).opsForValue();
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void isBlacklistedTrueWhenKeyExists() {
        when(redis.hasKey(KEY)).thenReturn(true);

        assertThat(service.isBlacklisted(TOKEN)).isTrue();
    }

    @Test
    void isBlacklistedFalseWhenKeyMissing() {
        when(redis.hasKey(KEY)).thenReturn(false);

        assertThat(service.isBlacklisted(TOKEN)).isFalse();
    }
}
