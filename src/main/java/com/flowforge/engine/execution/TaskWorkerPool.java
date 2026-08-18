package com.flowforge.engine.execution;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bounded worker pool that actually runs task attempts off the request thread.
 *
 * <p>We use a raw {@link ThreadPoolExecutor} (rather than hiding it behind
 * {@code @Async}) so the concurrency model is explicit and defensible:
 * <ul>
 *   <li><b>Bounded core/max threads</b> - the database and downstream services are the
 *       scarce resources, so we cap how many tasks run at once instead of spawning a
 *       thread per task.</li>
 *   <li><b>Bounded queue</b> - a {@link LinkedBlockingQueue} with a fixed capacity gives
 *       back-pressure instead of unbounded memory growth under load.</li>
 *   <li><b>{@link ThreadPoolExecutor.CallerRunsPolicy}</b> - when the queue is full, the
 *       submitting thread runs the task itself. That naturally throttles producers rather
 *       than dropping work.</li>
 *   <li><b>Named threads</b> - {@code ff-worker-N} makes thread dumps and logs readable
 *       when profiling.</li>
 *   <li><b>Graceful shutdown</b> - {@link #shutdown()} stops accepting new work and waits
 *       for in-flight tasks, so we don't kill running executions on redeploy.</li>
 * </ul>
 */
@Component
public class TaskWorkerPool {

    private static final Logger log = LoggerFactory.getLogger(TaskWorkerPool.class);

    private final ThreadPoolExecutor pool;

    public TaskWorkerPool(
            @Value("${flowforge.engine.core-pool-size:4}") int corePoolSize,
            @Value("${flowforge.engine.max-pool-size:8}") int maxPoolSize,
            @Value("${flowforge.engine.queue-capacity:100}") int queueCapacity,
            MeterRegistry meterRegistry) {

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ff-worker-" + seq.getAndIncrement());
                t.setDaemon(false); // let graceful shutdown drain them
                return t;
            }
        };

        this.pool = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
        log.info("TaskWorkerPool started (core={}, max={}, queue={})",
                corePoolSize, maxPoolSize, queueCapacity);

        // Expose live pool health as gauges so we can watch saturation in metrics.
        Gauge.builder("flowforge.worker.pool.active", pool, ThreadPoolExecutor::getActiveCount)
                .description("Worker threads currently executing a task")
                .register(meterRegistry);
        Gauge.builder("flowforge.worker.pool.queue.size", pool, p -> p.getQueue().size())
                .description("Tasks waiting in the worker queue")
                .register(meterRegistry);
    }

    /** The executor to hand to {@code CompletableFuture.*Async(..., executor)}. */
    public Executor executor() {
        return pool;
    }

    @PreDestroy
    public void shutdown() {
        log.info("TaskWorkerPool shutting down; draining in-flight tasks...");
        pool.shutdown(); // stop accepting new tasks; let queued/running finish
        try {
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Workers did not finish in 30s; forcing shutdown");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }
}
