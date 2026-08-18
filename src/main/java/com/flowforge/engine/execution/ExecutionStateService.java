package com.flowforge.engine.execution;

import com.flowforge.domain.entity.TaskExecution;
import com.flowforge.domain.entity.WorkflowExecution;
import com.flowforge.domain.enums.ExecutionStatus;
import com.flowforge.domain.enums.TaskExecutionStatus;
import com.flowforge.domain.repository.WorkflowExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transactional reads/writes on the execution aggregate that the {@link WorkflowRunner}
 * needs. Kept separate from the runner because the runner itself is NOT transactional
 * (it orchestrates across many short transactions on pool threads), and a bean can't
 * call its own {@code @Transactional} method through the proxy.
 */
@Service
public class ExecutionStateService {

    /**
     * A task in the execution's dependency DAG, identified by its {@code task_executions}
     * id, with the ids of the task executions that must complete first.
     */
    public record TaskNode(Long taskExecutionId, Set<Long> predecessorTaskExecutionIds) {
    }

    private final WorkflowExecutionRepository executionRepository;

    public ExecutionStateService(WorkflowExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    /**
     * Loads the DAG of task executions for one execution, mapping step-level dependency
     * edges onto the concrete task-execution ids. Read-only transaction; lazy collections
     * are traversed here while the session is open.
     */
    @Transactional(readOnly = true)
    public List<TaskNode> loadNodes(Long executionId) {
        WorkflowExecution execution = executionRepository.findById(executionId).orElseThrow();
        List<TaskExecution> tasks = execution.getTaskExecutions();

        // stepId -> taskExecutionId, so we can translate step dependencies into task deps.
        Map<Long, Long> stepToTask = new HashMap<>();
        for (TaskExecution t : tasks) {
            stepToTask.put(t.getWorkflowStep().getId(), t.getId());
        }

        List<TaskNode> nodes = new ArrayList<>(tasks.size());
        for (TaskExecution t : tasks) {
            Set<Long> predecessors = t.getWorkflowStep().getDependencies().stream()
                    .map(dep -> stepToTask.get(dep.getId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            nodes.add(new TaskNode(t.getId(), predecessors));
        }
        return nodes;
    }

    @Transactional
    public void markRunning(Long executionId) {
        WorkflowExecution execution = executionRepository.findById(executionId).orElseThrow();
        execution.setStatus(ExecutionStatus.RUNNING);
        if (execution.getStartedAt() == null) {
            execution.setStartedAt(Instant.now());
        }
    }

    /**
     * Computes and persists the terminal (or in-progress) execution status from its
     * tasks: any hard failure/cancel -> FAILED; else any not-yet-final task -> RUNNING;
     * else all succeeded -> SUCCEEDED.
     */
    @Transactional
    public ExecutionStatus finalizeExecution(Long executionId) {
        WorkflowExecution execution = executionRepository.findById(executionId).orElseThrow();
        List<TaskExecution> tasks = execution.getTaskExecutions();

        boolean anyHardFailure = tasks.stream().anyMatch(t ->
                t.getStatus() == TaskExecutionStatus.PERMANENT_FAILURE
                        || t.getStatus() == TaskExecutionStatus.DEAD_LETTER
                        || t.getStatus() == TaskExecutionStatus.CANCELLED);
        boolean anyInProgress = tasks.stream().anyMatch(t ->
                t.getStatus() == TaskExecutionStatus.PENDING
                        || t.getStatus() == TaskExecutionStatus.RUNNING
                        || t.getStatus() == TaskExecutionStatus.RETRYABLE_FAILURE
                        || t.getStatus() == TaskExecutionStatus.PENDING_APPROVAL);

        ExecutionStatus status;
        if (anyHardFailure) {
            status = ExecutionStatus.FAILED;
        } else if (anyInProgress) {
            status = ExecutionStatus.RUNNING; // waiting on retries/approvals
        } else {
            status = ExecutionStatus.SUCCEEDED;
        }

        execution.setStatus(status);
        if (status != ExecutionStatus.RUNNING) {
            execution.setFinishedAt(Instant.now());
        }
        return status;
    }
}
