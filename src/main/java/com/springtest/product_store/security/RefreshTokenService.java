package com.springtest.product_store.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

// Opaque, single-use refresh tokens stored in Redis (hashed) with a TTL.
// Consumed tokens are remembered, so a replayed one is recognised as reuse (likely theft).
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";
    private static final String USED_KEY_PREFIX = "refresh-used:";
    private static final char SEPARATOR = '|';
    private static final SecureRandom RANDOM = new SecureRandom();

    // Atomically: take the token (GETDEL) and, if it existed, remember it as used for as long
    // as it could have lived; otherwise report whether it had already been used.
    // One script so a concurrent replay can't slip between the delete and the "used" marker.
    private static final RedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GETDEL', KEYS[1])
            if value then
              redis.call('SET', KEYS[2], value, 'PX', ARGV[1])
              return {'VALID', value}
            end
            local used = redis.call('GET', KEYS[2])
            if used then
              return {'REUSED', used}
            end
            return {'UNKNOWN', ''}
            """, List.class);

    private final StringRedisTemplate redis;
    private final Duration validity;

    public RefreshTokenService(StringRedisTemplate redis,
                               @Value("${jwt.refresh-token-expiration}") Duration validity) {
        this.redis = redis;
        this.validity = validity;
    }

    // Owner and issue time of a refresh token
    public record Consumed(String email, long issuedAtMillis) {
    }

    public enum Status {
        // first use: rotate as usual
        VALID,
        // already used once: someone is replaying it
        REUSED,
        // never issued, expired, or revoked by logout
        UNKNOWN
    }

    // token is set for VALID and REUSED (the owner), null for UNKNOWN
    public record ConsumeResult(Status status, Consumed token) {
    }

    // Stored as "email|issuedAtMillis" so a session revocation can reject older tokens
    public String issue(String email) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(token), email + SEPARATOR + System.currentTimeMillis(), validity);
        return token;
    }

    // Single use: a VALID token is deleted and remembered, so presenting it again is REUSED
    public ConsumeResult consume(String token) {
        String hash = TokenHasher.sha256(token);
        @SuppressWarnings("unchecked")
        List<String> result = redis.execute(CONSUME_SCRIPT,
                List.of(KEY_PREFIX + hash, USED_KEY_PREFIX + hash),
                Long.toString(validity.toMillis()));
        Status status = Status.valueOf(result.get(0));
        return new ConsumeResult(status, status == Status.UNKNOWN ? null : parse(result.get(1)));
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

    // Logout: the token just stops existing (a later replay is UNKNOWN, not REUSED)
    public void revoke(String token) {
        redis.delete(key(token));
    }

    private String key(String token) {
        return KEY_PREFIX + TokenHasher.sha256(token);
    }
}
