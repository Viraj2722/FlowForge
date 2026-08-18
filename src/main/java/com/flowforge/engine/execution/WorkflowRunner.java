package com.flowforge.engine.execution;

import com.flowforge.domain.enums.ExecutionStatus;
import com.flowforge.domain.enums.TaskExecutionStatus;
import com.flowforge.engine.execution.ExecutionStateService.TaskNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates one workflow execution: runs its tasks concurrently while respecting the
 * dependency DAG. This class is intentionally NOT {@code @Transactional} - it coordinates
 * many short transactions (one per task, via {@link TaskExecutionWorker}) that run on the
 * {@link TaskWorkerPool}.
 *
 * <p>How the concurrency works:
 * <ol>
 *   <li>Load the task DAG ({@link ExecutionStateService#loadNodes}).</li>
 *   <li><b>Topologically sort</b> it (Kahn's algorithm) so every task appears after its
 *       predecessors - this also detects cycles.</li>
 *   <li>Build one {@link CompletableFuture} per task: a task's future is
 *       {@code allOf(predecessorFutures).thenApplyAsync(run-or-cancel, pool)}. Independent
 *       tasks therefore run in parallel; a task starts only once all its predecessors have
 *       completed, and is CANCELLED if any predecessor didn't succeed.</li>
 *   <li>{@code join()} on all futures, then compute the final execution status.</li>
 * </ol>
 */
@Service
public class WorkflowRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRunner.class);

    private final ExecutionStateService stateService;
    private final TaskExecutionWorker worker;
    private final TaskWorkerPool pool;

    public WorkflowRunner(ExecutionStateService stateService,
                          TaskExecutionWorker worker,
                          TaskWorkerPool pool) {
        this.stateService = stateService;
        this.worker = worker;
        this.pool = pool;
    }

    /** Runs the execution to a resting state and returns the resulting status. Blocking. */
    public ExecutionStatus run(Long executionId) {
        MDC.put("executionId", String.valueOf(executionId));
        try {
            return doRun(executionId);
        } finally {
            MDC.remove("executionId");
        }
    }

    private ExecutionStatus doRun(Long executionId) {
        log.info("Running execution {}", executionId);
        stateService.markRunning(executionId);

        List<TaskNode> nodes = stateService.loadNodes(executionId);
        Map<Long, TaskNode> byId = new HashMap<>();
        nodes.forEach(n -> byId.put(n.taskExecutionId(), n));

        List<Long> order = topologicalOrder(byId);

        // Build the future graph in topological order so predecessors' futures exist first.
        Map<Long, CompletableFuture<TaskExecutionStatus>> futures = new ConcurrentHashMap<>();
        for (Long id : order) {
            TaskNode node = byId.get(id);
            CompletableFuture<Void> predecessorsDone = node.predecessorTaskExecutionIds().isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.allOf(node.predecessorTaskExecutionIds().stream()
                    .map(futures::get)
                    .toArray(CompletableFuture[]::new));

            CompletableFuture<TaskExecutionStatus> future = predecessorsDone.thenApplyAsync(ignored -> {
                boolean allSucceeded = node.predecessorTaskExecutionIds().stream()
                        .allMatch(p -> futures.get(p).join() == TaskExecutionStatus.SUCCEEDED);
                return allSucceeded
                        ? worker.execute(id)
                        : worker.cancel(id, "a predecessor task did not succeed");
            }, pool.executor());

            futures.put(id, future);
        }

        // Wait for the whole graph, then compute the execution's status.
        CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();
        ExecutionStatus status = stateService.finalizeExecution(executionId);
        log.info("Execution {} finished with status {}", executionId, status);
        return status;
    }

    /**
     * Kahn's algorithm: returns task ids ordered so each appears after all its
     * predecessors. Throws if the graph contains a cycle (which a valid DAG never should).
     */
    private List<Long> topologicalOrder(Map<Long, TaskNode> byId) {
        Map<Long, Integer> indegree = new LinkedHashMap<>();
        Map<Long, List<Long>> successors = new HashMap<>();
        for (Long id : byId.keySet()) {
            indegree.put(id, 0);
            successors.put(id, new ArrayList<>());
        }
        for (TaskNode node : byId.values()) {
            for (Long predecessor : node.predecessorTaskExecutionIds()) {
                successors.get(predecessor).add(node.taskExecutionId());
                indegree.merge(node.taskExecutionId(), 1, Integer::sum);
            }
        }

        Deque<Long> ready = new ArrayDeque<>();
        indegree.forEach((id, deg) -> {
            if (deg == 0) {
                ready.add(id);
            }
        });

        List<Long> order = new ArrayList<>(byId.size());
        while (!ready.isEmpty()) {
            Long id = ready.poll();
            order.add(id);
            for (Long next : successors.get(id)) {
                if (indegree.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }

        if (order.size() != byId.size()) {
            throw new IllegalStateException("Workflow execution " + byId.keySet()
                    + " has a dependency cycle; cannot run");
        }
        return order;
    }
}
