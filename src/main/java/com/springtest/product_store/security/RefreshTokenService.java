package com.springtest.product_store.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

// Opaque, single-use refresh tokens stored in Redis (hashed) with a TTL
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final Duration validity;

    public RefreshTokenService(StringRedisTemplate redis,
                               @Value("${jwt.refresh-token-expiration}") Duration validity) {
        this.redis = redis;
        this.validity = validity;
    }

    public String issue(String email) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(token), email, validity);
        return token;
    }

    // Returns the owner's email and deletes the token atomically (GETDEL),
    // so each refresh token can be used exactly once (rotation).
    public Optional<String> consume(String token) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(key(token)));
    }

    public void revoke(String token) {
        redis.delete(key(token));
    }

    private String key(String token) {
        return KEY_PREFIX + TokenHasher.sha256(token);
    }
}
