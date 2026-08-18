package com.flowforge.engine.execution;

import com.flowforge.domain.enums.ExecutionStatus;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Fire-and-forget entry point for running an execution off the HTTP thread.
 *
 * <p>The REST {@code start} endpoint returns 202 immediately; the actual orchestration
 * (which blocks while tasks run) happens here on a dedicated single-thread executor.
 * Using a separate launcher thread - not the {@link TaskWorkerPool} - keeps the
 * orchestration wait from consuming a worker slot.
 *
 * <p>Exceptions are caught and logged: a background run must never bubble an unhandled
 * exception out of the executor (it would just be swallowed and lost).
 */
@Component
public class ExecutionLauncher {

    private static final Logger log = LoggerFactory.getLogger(ExecutionLauncher.class);

    private final WorkflowRunner runner;
    private final ExecutorService launcherExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ff-launcher");
                t.setDaemon(false);
                return t;
            });

    /**
     * Execution ids currently being run. Guards against the same execution being launched
     * twice concurrently - e.g. the retry scheduler and a manual /start firing together.
     * A concurrent set is exactly right here: many threads may call {@link #launch}.
     */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public ExecutionLauncher(WorkflowRunner runner) {
        this.runner = runner;
    }

    /**
     * Submits the execution to run in the background, unless it is already running.
     * Returns immediately; the returned future completes when the run finishes.
     */
    public CompletableFuture<ExecutionStatus> launch(Long executionId) {
        if (!inFlight.add(executionId)) {
            log.debug("Execution {} is already in-flight; skipping duplicate launch", executionId);
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture
                .supplyAsync(() -> runner.run(executionId), launcherExecutor)
                .handle((status, ex) -> {
                    inFlight.remove(executionId);
                    if (ex != null) {
                        log.error("Execution {} failed to run", executionId, ex);
                        return ExecutionStatus.FAILED;
                    }
                    return status;
                });
    }

    @PreDestroy
    public void shutdown() {
        launcherExecutor.shutdown();
        try {
            if (!launcherExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                launcherExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            launcherExecutor.shutdownNow();
        }
    }
}
