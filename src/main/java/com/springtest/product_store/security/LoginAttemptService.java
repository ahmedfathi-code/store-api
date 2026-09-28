package com.springtest.product_store.security;

import com.springtest.product_store.exception.TooManyAttemptsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

// Counts failed password attempts in fixed Redis windows and blocks further attempts
// (429) once a limit is reached. Only failures count, so normal users are never throttled.
// Emails are hashed in keys, like tokens.
@Service
public class LoginAttemptService {

    // A counter key and the number of failures it allows per window
    public record Limit(String key, long maxFailures) {
    }

    private final StringRedisTemplate redis;
    private final Duration window;
    private final long loginPerAccount;
    private final long loginPerIp;
    private final long changePassword;

    public LoginAttemptService(StringRedisTemplate redis,
                               @Value("${rate-limit.window}") Duration window,
                               @Value("${rate-limit.login.per-account}") long loginPerAccount,
                               @Value("${rate-limit.login.per-ip}") long loginPerIp,
                               @Value("${rate-limit.change-password}") long changePassword) {
        this.redis = redis;
        this.window = window;
        this.loginPerAccount = loginPerAccount;
        this.loginPerIp = loginPerIp;
        this.changePassword = changePassword;
    }

    // Per IP+email (so an attacker can't lock a victim out from elsewhere) and per IP
    // (one machine trying many accounts)
    public List<Limit> loginLimits(String ip, String email) {
        return List.of(
                new Limit("ratelimit:login:" + ip + ":" + TokenHasher.sha256(normalize(email)), loginPerAccount),
                new Limit("ratelimit:login-ip:" + ip, loginPerIp));
    }

    public List<Limit> changePasswordLimits(String email) {
        return List.of(new Limit("ratelimit:change-password:" + TokenHasher.sha256(normalize(email)), changePassword));
    }

    // Throws TooManyAttemptsException (with seconds until the window ends) if any limit is reached
    public void checkAllowed(List<Limit> limits) {
        for (Limit limit : limits) {
            String count = redis.opsForValue().get(limit.key());
            if (count != null && Long.parseLong(count) >= limit.maxFailures()) {
                throw new TooManyAttemptsException(secondsLeft(limit.key()));
            }
        }
    }

    public void recordFailure(List<Limit> limits) {
        for (Limit limit : limits) {
            Long count = redis.opsForValue().increment(limit.key());
            // the window starts at the first failure
            if (count != null && count == 1) {
                redis.expire(limit.key(), window);
            }
        }
    }

    // After a success: forget this account's failures (the IP-wide counter keeps running)
    public void reset(Limit limit) {
        redis.delete(limit.key());
    }

    private long secondsLeft(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            // counter without an expiry (e.g. EXPIRE lost after INCR): give it one now
            redis.expire(key, window);
            return window.toSeconds();
        }
        return Math.max(1, ttl);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
