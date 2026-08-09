package com.flowforge.engine.retry;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable exponential-backoff retry policy.
 *
 * <p>The delay before the next attempt grows geometrically:
 * <pre>
 *   delay(attempt) = min( initialDelay * multiplier^(attempt - 1), maxDelay )
 * </pre>
 * With the defaults (initial = 1s, multiplier = 2) that yields the classic curve:
 * <pre>
 *   after attempt 1 fails -> wait 1s
 *   after attempt 2 fails -> wait 2s
 *   after attempt 3 fails -> wait 4s
 *   after attempt 4 fails -> wait 8s ...
 * </pre>
 *
 * <p><b>Why exponential backoff?</b> A fixed short retry interval can turn a brief
 * downstream outage into a self-inflicted denial-of-service ("retry storm"). Backing
 * off exponentially gives the dependency room to recover while still retrying
 * promptly at first. The {@code maxDelay} cap stops the wait from growing without
 * bound (you don't want a 34-minute wait on attempt 12).
 *
 * <p><b>Why a record with a validating constructor?</b> The policy is a value object:
 * two policies with the same fields are equal, and it can never be in an invalid
 * state (validated once, at construction). Being immutable, one instance is safely
 * shared across threads.
 *
 * <p>Note: this core policy is intentionally <em>deterministic</em> (no random
 * jitter) so it is trivial to unit-test. Jitter — randomising the delay to avoid many
 * clients retrying in lockstep — is a real production concern and is added as an
 * explicit, separately-tested decorator later, not baked into the math here.
 *
 * @param maxAttempts  total attempts allowed, including the first (must be &gt;= 1)
 * @param initialDelay delay after the first failed attempt (must be &gt; 0)
 * @param multiplier   growth factor per attempt (must be &gt;= 1.0)
 * @param maxDelay     upper cap on any single delay (must be &gt;= initialDelay)
 */
public record RetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        double multiplier,
        Duration maxDelay
) {

    public RetryPolicy {
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(maxDelay, "maxDelay");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1 but was " + maxAttempts);
        }
        if (initialDelay.isZero() || initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must be > 0 but was " + initialDelay);
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0 but was " + multiplier);
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException(
                    "maxDelay (" + maxDelay + ") must be >= initialDelay (" + initialDelay + ")");
        }
    }

    /** Sensible default: 4 attempts, 1s base, doubling, capped at 30s. */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(4, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30));
    }

    /**
     * Delay to wait <em>after</em> the given attempt has failed, before the next one.
     *
     * @param attempt the 1-based number of the attempt that just failed
     * @return the backoff delay, never exceeding {@link #maxDelay}
     * @throws IllegalArgumentException if {@code attempt < 1}
     */
    public Duration delayForAttempt(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1 but was " + attempt);
        }
        // Compute in double then clamp. We clamp before converting to avoid any
        // long overflow when the exponential grows large.
        double factor = Math.pow(multiplier, attempt - 1);
        double millis = initialDelay.toMillis() * factor;
        long capMillis = maxDelay.toMillis();
        if (millis >= capMillis) {
            return maxDelay;
        }
        return Duration.ofMillis((long) millis);
    }

    /**
     * Should another attempt be made after the given attempt failed?
     *
     * @param attempt the 1-based number of the attempt that just failed
     * @return true if {@code attempt} is below {@link #maxAttempts}
     */
    public boolean shouldRetry(int attempt) {
        return attempt < maxAttempts;
    }
}
