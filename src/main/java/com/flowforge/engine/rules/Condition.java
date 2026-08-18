package com.flowforge.engine.rules;

/**
 * A boolean predicate over a {@link RuleContext} - the building block of a rule.
 *
 * <p>Declared as a {@link FunctionalInterface} so conditions can be written as lambdas,
 * and given {@code and}/{@code or}/{@code negate} default methods so they compose
 * fluently (composition over a big boolean expression):
 * <pre>{@code
 *   Conditions.eq("priority", HIGH).and(Conditions.gt("amount", 100_000))
 * }</pre>
 * This is the same design the JDK uses for {@link java.util.function.Predicate}; we keep
 * our own type so conditions read against a {@code RuleContext} rather than a raw object.
 */
@FunctionalInterface
public interface Condition {

    boolean test(RuleContext context);

    default Condition and(Condition other) {
        return ctx -> this.test(ctx) && other.test(ctx);
    }

    default Condition or(Condition other) {
        return ctx -> this.test(ctx) || other.test(ctx);
    }

    default Condition negate() {
        return ctx -> !this.test(ctx);
    }
}
