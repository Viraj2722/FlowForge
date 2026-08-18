package com.flowforge.engine;

import com.flowforge.engine.handlers.ApprovalTaskHandler;
import com.flowforge.engine.handlers.CustomJavaTaskHandler;
import com.flowforge.engine.handlers.CustomTaskAction;
import com.flowforge.engine.handlers.EmailNotificationTaskHandler;
import com.flowforge.engine.handlers.WebhookTaskHandler;
import com.flowforge.engine.model.Priority;
import com.flowforge.engine.model.TaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A simple, reproducible benchmark that quantifies the benefit of the engine's concurrent
 * execution over running the same tasks one-by-one.
 *
 * <p>Each task simulates I/O latency (a short sleep), which is the realistic case for
 * FlowForge's task types (email, webhook) - they spend their time waiting on the network,
 * not burning CPU. For I/O-bound work, concurrency wins big; for pure CPU work it would
 * only help up to the core count. Framing that trade-off correctly is the point.
 *
 * <p>Disabled by default (it sleeps on purpose). Run it explicitly:
 * <pre>
 *   FLOWFORGE_BENCHMARK=true mvn -Dtest=ConcurrencyBenchmarkTest -DfailIfNoTests=false test
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "FLOWFORGE_BENCHMARK", matches = "true")
class ConcurrencyBenchmarkTest {

    private static final int TASK_COUNT = 16;
    private static final long SIMULATED_IO_MILLIS = 50;
    private static final int POOL_SIZE = 8;

    @Test
    void concurrentExecutionIsFasterThanSequentialForIoBoundTasks() throws Exception {
        TaskDispatcher dispatcher = new TaskDispatcher(new TaskHandlerRegistry(List.of(
                new EmailNotificationTaskHandler(),
                new WebhookTaskHandler(),
                new ApprovalTaskHandler(),
                new CustomJavaTaskHandler())));

        List<TaskContext> tasks = buildIoBoundTasks();

        long sequentialMs = timeSequential(dispatcher, tasks);
        long concurrentMs = timeConcurrent(dispatcher, tasks);
        double speedup = (double) sequentialMs / Math.max(1, concurrentMs);

        System.out.printf("%n=== FlowForge concurrency benchmark ===%n");
        System.out.printf("tasks=%d, simulatedIO=%dms/task, poolSize=%d%n",
                TASK_COUNT, SIMULATED_IO_MILLIS, POOL_SIZE);
        System.out.printf("sequential: %5d ms%n", sequentialMs);
        System.out.printf("concurrent: %5d ms%n", concurrentMs);
        System.out.printf("speedup   : %.1fx%n", speedup);
        System.out.printf("=======================================%n");

        // Concurrency must clearly help for I/O-bound work.
        assertThat(concurrentMs).isLessThan(sequentialMs);
    }

    private List<TaskContext> buildIoBoundTasks() {
        CustomTaskAction sleepThenSucceed = ctx -> {
            Thread.sleep(SIMULATED_IO_MILLIS);
            return TaskResult.success("done");
        };
        List<TaskContext> tasks = new ArrayList<>(TASK_COUNT);
        for (int i = 0; i < TASK_COUNT; i++) {
            tasks.add(new TaskContext(
                    "task-" + i, TaskType.CUSTOM, Priority.MEDIUM, 1,
                    Map.of(CustomJavaTaskHandler.ACTION_PARAM, sleepThenSucceed), "bench"));
        }
        return tasks;
    }

    private long timeSequential(TaskDispatcher dispatcher, List<TaskContext> tasks) {
        long start = System.nanoTime();
        for (TaskContext task : tasks) {
            dispatcher.dispatch(task);
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private long timeConcurrent(TaskDispatcher dispatcher, List<TaskContext> tasks) {
        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
        try {
            long start = System.nanoTime();
            CompletableFuture<?>[] futures = tasks.stream()
                    .map(task -> CompletableFuture.runAsync(() -> dispatcher.dispatch(task), pool))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        } finally {
            pool.shutdown();
        }
    }
}
