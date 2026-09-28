package com.springtest.product_store.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

// Revokes every session of a user at once (e.g. after a password change) by storing a
// "valid after" timestamp: any access or refresh token issued before it is rejected.
// The key expires with the longest token lifetime, after which no older token can exist.
@Service
public class SessionRevocationService {

    private static final String KEY_PREFIX = "sessions-valid-after:";

    private final StringRedisTemplate redis;
    private final Duration longestTokenLifetime;
    private final Clock clock;

    @Autowired
    public SessionRevocationService(StringRedisTemplate redis,
                                    @Value("${jwt.refresh-token-expiration}") Duration longestTokenLifetime) {
        this(redis, longestTokenLifetime, Clock.systemUTC());
    }

    SessionRevocationService(StringRedisTemplate redis, Duration longestTokenLifetime, Clock clock) {
        this.redis = redis;
        this.longestTokenLifetime = longestTokenLifetime;
        this.clock = clock;
    }

    // Tokens issued before the returned instant (epoch millis) stop working
    public long revokeAllSessions(String email) {
        long now = clock.millis();
        redis.opsForValue().set(key(email), Long.toString(now), longestTokenLifetime);
        return now;
    }

    public boolean isRevoked(String email, long issuedAtMillis) {
        String validAfter = redis.opsForValue().get(key(email));
        return validAfter != null && issuedAtMillis < Long.parseLong(validAfter);
    }

    private static String key(String email) {
        return KEY_PREFIX + email;
    }
}
