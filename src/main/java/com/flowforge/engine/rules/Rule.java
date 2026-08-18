package com.flowforge.engine.rules;

import java.util.Optional;

/**
 * A single business rule: a named {@link Condition} that, when it matches, yields a
 * {@link RuleOutcome}. {@code order} controls precedence in a first-match engine (lower
 * runs first).
 */
public record Rule(String name, int order, Condition condition, RuleOutcome outcome) {

    /** Returns the outcome if this rule's condition matches the context. */
    public Optional<RuleOutcome> evaluate(RuleContext context) {
        return condition.test(context) ? Optional.of(outcome) : Optional.empty();
    }
}
