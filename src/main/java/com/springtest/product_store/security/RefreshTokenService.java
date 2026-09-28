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
    private static final char SEPARATOR = '|';
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final Duration validity;

    public RefreshTokenService(StringRedisTemplate redis,
                               @Value("${jwt.refresh-token-expiration}") Duration validity) {
        this.redis = redis;
        this.validity = validity;
    }

    // Owner and issue time of a consumed refresh token
    public record Consumed(String email, long issuedAtMillis) {
    }

    // Stored as "email|issuedAtMillis" so a session revocation can reject older tokens
    public String issue(String email) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(token), email + SEPARATOR + System.currentTimeMillis(), validity);
        return token;
    }

    // Returns the owner and issue time and deletes the token atomically (GETDEL),
    // so each refresh token can be used exactly once (rotation).
    public Optional<Consumed> consume(String token) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(key(token)))
                .map(RefreshTokenService::parse);
    }

    // Values stored before issue times were recorded are just the email: treat them as
    // issued at 0, so any session revocation covers them (otherwise they keep working)
    static Consumed parse(String value) {
        int separator = value.lastIndexOf(SEPARATOR);
        if (separator < 0) {
            return new Consumed(value, 0L);
        }
        try {
            return new Consumed(value.substring(0, separator), Long.parseLong(value.substring(separator + 1)));
        } catch (NumberFormatException e) {
            // an email containing '|' (legal, if unusual) stored in the old format
            return new Consumed(value, 0L);
        }
    }

    public void revoke(String token) {
        redis.delete(key(token));
    }

    private String key(String token) {
        return KEY_PREFIX + TokenHasher.sha256(token);
    }
}
