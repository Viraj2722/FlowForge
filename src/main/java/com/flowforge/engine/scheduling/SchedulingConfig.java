package com.flowforge.engine.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns Spring's scheduling infrastructure on (so {@code @Scheduled} methods fire).
 *
 * <p>Gated by {@code flowforge.scheduler.enabled} (default true). Tests set it to false so
 * the timers never race with integration tests - the {@link ExecutionScheduler} bean still
 * exists, so tests can call its poll methods directly and deterministically.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "flowforge.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
