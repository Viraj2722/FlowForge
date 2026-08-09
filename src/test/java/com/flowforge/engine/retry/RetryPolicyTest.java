package com.flowforge.engine.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    @ParameterizedTest(name = "attempt {0} -> {1} ms delay")
    @CsvSource({
            "1, 1000",   // 1s * 2^0
            "2, 2000",   // 1s * 2^1
            "3, 4000",   // 1s * 2^2
            "4, 8000",   // 1s * 2^3
            "5, 16000"   // 1s * 2^4
    })
    @DisplayName("delay doubles each attempt (1,2,4,8,16 seconds)")
    void exponentialBackoffFollowsTheExpectedCurve(int attempt, long expectedMillis) {
        // maxDelay large enough that the cap does not interfere
        RetryPolicy policy = new RetryPolicy(10, Duration.ofSeconds(1), 2.0, Duration.ofMinutes(1));

        assertThat(policy.delayForAttempt(attempt)).isEqualTo(Duration.ofMillis(expectedMillis));
    }

    @Test
    @DisplayName("delay is capped at maxDelay once the curve exceeds it")
    void delayIsCappedAtMaxDelay() {
        RetryPolicy policy = new RetryPolicy(20, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(5));

        // attempt 3 would be 4s (under cap), attempt 4 would be 8s -> capped to 5s
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.delayForAttempt(50)).isEqualTo(Duration.ofSeconds(5)); // no overflow
    }

    @Test
    @DisplayName("shouldRetry is true until the attempt cap is reached")
    void shouldRetryRespectsMaxAttempts() {
        RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30));

        assertThat(policy.shouldRetry(1)).isTrue();  // after attempt 1 of 3 -> retry
        assertThat(policy.shouldRetry(2)).isTrue();  // after attempt 2 of 3 -> retry
        assertThat(policy.shouldRetry(3)).isFalse(); // after attempt 3 of 3 -> stop (dead-letter)
    }

    @Test
    @DisplayName("default policy is 4 attempts, 1s base, x2, capped at 30s")
    void defaultPolicyHasSensibleValues() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();

        assertThat(policy.maxAttempts()).isEqualTo(4);
        assertThat(policy.initialDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.multiplier()).isEqualTo(2.0);
        assertThat(policy.maxDelay()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("invalid configuration is rejected at construction (fail fast)")
    void invalidConfigurationIsRejected() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");

        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ZERO, 2.0, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialDelay");

        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofSeconds(1), 0.5, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiplier");

        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofSeconds(10), 2.0, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDelay");
    }

    @Test
    @DisplayName("delayForAttempt rejects attempt < 1")
    void delayForAttemptRejectsNonPositiveAttempt() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();

        assertThatThrownBy(() -> policy.delayForAttempt(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
