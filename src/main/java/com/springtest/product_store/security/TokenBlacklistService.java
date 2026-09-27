package com.springtest.product_store.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Revoked access tokens, kept in Redis only until they would have expired anyway
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redis;

    public TokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void blacklist(String token, Duration remainingValidity) {
        // An already-expired token is rejected by signature/expiry checks anyway
        if (remainingValidity.isZero() || remainingValidity.isNegative()) {
            return;
        }
        redis.opsForValue().set(key(token), "1", remainingValidity);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redis.hasKey(key(token)));
    }

    private String key(String token) {
        return KEY_PREFIX + TokenHasher.sha256(token);
    }
}
