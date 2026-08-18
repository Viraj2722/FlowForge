package com.flowforge.domain.entity;

import com.flowforge.engine.model.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A task that exhausted its retries or failed permanently, captured for inspection and
 * manual replay. One-to-one with the originating {@link TaskExecution} (enforced by the
 * unique FK in the schema).
 */
@Entity
@Table(name = "dead_letter_tasks")
public class DeadLetterTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_execution_id", nullable = false, unique = true)
    private TaskExecution taskExecution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_execution_id", nullable = false)
    private WorkflowExecution workflowExecution;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    private TaskType taskType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String payload;

    @Column(name = "last_error")
    private String lastError;

    @Column(nullable = false)
    private int attempts;

    @CreationTimestamp
    @Column(name = "failed_at", nullable = false, updatable = false)
    private Instant failedAt;

    @Column(nullable = false)
    private boolean replayed = false;

    @Column(name = "replayed_at")
    private Instant replayedAt;

    protected DeadLetterTask() {
    }

    public DeadLetterTask(TaskExecution taskExecution, WorkflowExecution workflowExecution,
                          TaskType taskType, int attempts, String lastError) {
        this.taskExecution = taskExecution;
        this.workflowExecution = workflowExecution;
        this.taskType = taskType;
        this.attempts = attempts;
        this.lastError = lastError;
    }

    public Long getId() {
        return id;
    }

    public TaskExecution getTaskExecution() {
        return taskExecution;
    }

    public WorkflowExecution getWorkflowExecution() {
        return workflowExecution;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getLastError() {
        return lastError;
    }

    public int getAttempts() {
        return attempts;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public void markReplayed() {
        this.replayed = true;
        this.replayedAt = Instant.now();
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public Instant getReplayedAt() {
        return replayedAt;
    }
}
