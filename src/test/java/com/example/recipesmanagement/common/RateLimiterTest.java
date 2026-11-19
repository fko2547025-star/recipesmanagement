package com.example.recipesmanagement.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 回数制限（RateLimiter）のテスト。
 */
class RateLimiterTest {

    @Test
    void 決まった回数までは使えてそれを超えると断る() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("test", "a", 3, Duration.ofHours(1))).isTrue();
        }
        assertThat(limiter.tryAcquire("test", "a", 3, Duration.ofHours(1))).isFalse();
    }

    @Test
    void 相手や種類が違えば別々に数える() {
        RateLimiter limiter = new RateLimiter();
        limiter.tryAcquire("test", "a", 1, Duration.ofHours(1));
        assertThat(limiter.tryAcquire("test", "b", 1, Duration.ofHours(1))).isTrue();
        assertThat(limiter.tryAcquire("other", "a", 1, Duration.ofHours(1))).isTrue();
    }

    @Test
    void 時間がたてばまた使える() throws Exception {
        RateLimiter limiter = new RateLimiter();
        assertThat(limiter.tryAcquire("test", "a", 1, Duration.ofMillis(50))).isTrue();
        assertThat(limiter.tryAcquire("test", "a", 1, Duration.ofMillis(50))).isFalse();
        Thread.sleep(80);
        assertThat(limiter.tryAcquire("test", "a", 1, Duration.ofMillis(50))).isTrue();
    }
}
