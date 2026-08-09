package com.flowforge.reporting.dto;

/**
 * Number of failed task executions broken down by task type, useful for spotting which
 * integration (email, webhook, ...) is the least reliable.
 */
public record TaskTypeFailureCount(String taskType, long failures) {
}
