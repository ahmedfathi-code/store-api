package com.springtest.product_store.security;

import com.springtest.product_store.exception.TooManyAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final LoginAttemptService.Limit LIMIT = new LoginAttemptService.Limit("ratelimit:test", 5);

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(redis, WINDOW, 5, 30, 5);
    }

    @Test
    void loginLimitsArePerIpAndEmailAndPerIpWithHashedNormalisedEmail() {
        List<LoginAttemptService.Limit> limits = service.loginLimits("10.0.0.1", "  User@Example.com ");

        String hashed = TokenHasher.sha256("user@example.com");
        assertThat(limits).containsExactly(
                new LoginAttemptService.Limit("ratelimit:login:10.0.0.1:" + hashed, 5),
                new LoginAttemptService.Limit("ratelimit:login-ip:10.0.0.1", 30));
        assertThat(limits.get(0).key()).doesNotContain("example.com");
    }

    @Test
    void firstFailureStartsTheWindow() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(LIMIT.key())).thenReturn(1L);

        service.recordFailure(List.of(LIMIT));

        verify(redis).expire(LIMIT.key(), WINDOW);
    }

    @Test
    void laterFailuresDoNotExtendTheWindow() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(LIMIT.key())).thenReturn(3L);

        service.recordFailure(List.of(LIMIT));

        verify(redis, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void allowedBelowTheLimit() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(LIMIT.key())).thenReturn("4");

        assertThatCode(() -> service.checkAllowed(List.of(LIMIT))).doesNotThrowAnyException();
    }

    @Test
    void allowedWithoutAnyFailures() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(LIMIT.key())).thenReturn(null);

        assertThatCode(() -> service.checkAllowed(List.of(LIMIT))).doesNotThrowAnyException();
    }

    @Test
    void blockedAtTheLimitWithSecondsLeftInTheWindow() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(LIMIT.key())).thenReturn("5");
        when(redis.getExpire(LIMIT.key(), TimeUnit.SECONDS)).thenReturn(420L);

        assertThatThrownBy(() -> service.checkAllowed(List.of(LIMIT)))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(420L));
    }

    @Test
    void counterWithoutExpiryGetsOneAndAFullWindowRetryAfter() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(LIMIT.key())).thenReturn("9");
        when(redis.getExpire(LIMIT.key(), TimeUnit.SECONDS)).thenReturn(-1L);

        assertThatThrownBy(() -> service.checkAllowed(List.of(LIMIT)))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(WINDOW.toSeconds()));
        verify(redis).expire(LIMIT.key(), WINDOW);
    }

    @Test
    void resetDeletesTheCounter() {
        service.reset(LIMIT);

        verify(redis).delete(LIMIT.key());
    }
}
