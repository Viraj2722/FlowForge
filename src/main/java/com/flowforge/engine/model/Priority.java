package com.flowforge.engine.model;

/**
 * Business priority of a workflow or task.
 *
 * <p>Used later by the rule engine (e.g. "IF priority == HIGH AND amount &gt; 100000
 * THEN route to SENIOR_APPROVER") and potentially by the scheduler to order work.
 *
 * <p>The explicit {@code weight} lets us compare priorities numerically without
 * relying on {@link Enum#ordinal()} — ordinal is fragile because reordering the
 * constants would silently change behaviour. This is a common interview gotcha.
 */
public enum Priority {

    LOW(10),
    MEDIUM(20),
    HIGH(30),
    CRITICAL(40);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    /** Higher weight == more urgent. Stable even if constants are reordered. */
    public int weight() {
        return weight;
    }
}
