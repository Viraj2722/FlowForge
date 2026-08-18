package com.flowforge.service;

import com.flowforge.engine.events.ExecutionTriggeredEvent;
import com.flowforge.engine.events.TaskDeadLetteredEvent;
import com.flowforge.engine.events.WorkflowCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns domain events into audit records.
 *
 * <p>Uses {@code @TransactionalEventListener(AFTER_COMMIT)} rather than a plain
 * {@code @EventListener}: audit rows are written only for changes that actually
 * committed. If the publishing transaction rolls back, no misleading audit entry is left
 * behind. The trade-off (audit is best-effort after commit, not atomic with the change)
 * is a deliberate, common choice for audit trails.
 *
 * <p>Decoupling via events keeps the services focused on business logic - they publish
 * "what happened" and don't know or care that auditing is a listener.
 */
@Component
public class AuditEventListener {

    private final AuditService auditService;

    public AuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowCreated(WorkflowCreatedEvent event) {
        auditService.record(event.actor(), "WORKFLOW_CREATED", "WORKFLOW", event.workflowId(),
                Map.of("name", event.name()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExecutionTriggered(ExecutionTriggeredEvent event) {
        auditService.record(event.actor(), "EXECUTION_TRIGGERED", "WORKFLOW_EXECUTION", event.executionId(),
                Map.of("workflowId", event.workflowId(), "correlationId", event.correlationId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskDeadLettered(TaskDeadLetteredEvent event) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("executionId", event.executionId());
        meta.put("taskType", event.taskType().name());
        meta.put("error", event.error());
        auditService.record("system", "TASK_DEAD_LETTERED", "TASK_EXECUTION", event.taskExecutionId(), meta);
    }
}
