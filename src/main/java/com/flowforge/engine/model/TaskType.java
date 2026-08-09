package com.flowforge.engine.model;

/**
 * The kinds of work a single workflow step can perform.
 *
 * <p>Each {@code TaskType} is handled by exactly one
 * {@link com.flowforge.engine.TaskHandler} (the Strategy pattern). Adding a new
 * task type means adding a new enum constant plus a new handler — we never edit a
 * giant {@code switch}. The {@link com.flowforge.engine.TaskHandlerRegistry}
 * guarantees at startup that every type has a handler.
 *
 * <p>These are simulated integrations (email/webhook) rather than real ones, on
 * purpose: the focus of FlowForge is the orchestration engine, not the I/O.
 */
public enum TaskType {

    /** Simulates sending an email / push / SMS notification. */
    EMAIL,

    /** Simulates calling an outbound HTTP webhook. */
    WEBHOOK,

    /** A human approval gate — pauses until a decision is recorded. */
    APPROVAL,

    /** An arbitrary in-process Java action supplied by the caller. */
    CUSTOM
}
