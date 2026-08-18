package com.flowforge.domain.enums;

/**
 * Lifecycle state of a workflow <em>definition</em>. Mirrors the CHECK constraint on
 * {@code workflows.status}. Stored as text via {@code @Enumerated(EnumType.STRING)}.
 */
public enum WorkflowStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
