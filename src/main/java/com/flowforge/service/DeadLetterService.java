package com.flowforge.service;

import com.flowforge.api.dto.response.DeadLetterResponse;
import com.flowforge.domain.entity.DeadLetterTask;
import com.flowforge.domain.entity.TaskExecution;
import com.flowforge.domain.entity.WorkflowExecution;
import com.flowforge.domain.enums.ExecutionStatus;
import com.flowforge.domain.enums.TaskExecutionStatus;
import com.flowforge.domain.repository.DeadLetterTaskRepository;
import com.flowforge.service.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inspection and replay of dead-lettered tasks.
 *
 * <p><b>Replay</b> resets the failed task back to PENDING (with a fresh attempt budget)
 * and puts its execution back to PENDING, then marks the dead-letter record as replayed.
 * The caller (controller) launches the execution afterwards, so the reset and the re-run
 * stay cleanly separated - and the reset is one atomic transaction.
 */
@Service
@Transactional(readOnly = true)
public class DeadLetterService {

    private final DeadLetterTaskRepository deadLetterRepository;

    public DeadLetterService(DeadLetterTaskRepository deadLetterRepository) {
        this.deadLetterRepository = deadLetterRepository;
    }

    public Page<DeadLetterResponse> listActive(Pageable pageable) {
        return deadLetterRepository.findByReplayedFalse(pageable).map(this::toResponse);
    }

    /**
     * Resets the dead-lettered task for another run and returns the execution id to launch.
     *
     * @throws ResourceNotFoundException if no such dead letter exists
     * @throws IllegalStateException     if it was already replayed
     */
    @Transactional
    public Long replay(Long deadLetterId) {
        DeadLetterTask dlt = deadLetterRepository.findById(deadLetterId)
                .orElseThrow(() -> new ResourceNotFoundException("DeadLetterTask", deadLetterId));
        if (dlt.isReplayed()) {
            throw new IllegalStateException("Dead letter " + deadLetterId + " was already replayed");
        }

        TaskExecution task = dlt.getTaskExecution();
        task.setStatus(TaskExecutionStatus.PENDING);
        task.setAttempt(0);              // give it a fresh attempt budget
        task.setNextRetryAt(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setLastError(null);

        WorkflowExecution execution = dlt.getWorkflowExecution();
        execution.setStatus(ExecutionStatus.PENDING);
        execution.setFinishedAt(null);

        dlt.markReplayed();
        return execution.getId();
    }

    private DeadLetterResponse toResponse(DeadLetterTask dlt) {
        return new DeadLetterResponse(
                dlt.getId(),
                dlt.getTaskExecution().getId(),
                dlt.getWorkflowExecution().getId(),
                dlt.getTaskType(),
                dlt.getAttempts(),
                dlt.getLastError(),
                dlt.getFailedAt(),
                dlt.isReplayed());
    }
}
