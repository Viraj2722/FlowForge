package com.flowforge.engine.rules;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * Factory of common {@link Condition}s. A tiny DSL so rule definitions read declaratively:
 * {@code eq("priority", HIGH)}, {@code gt("amount", 100_000)}.
 *
 * <p>Numeric comparisons go through {@link BigDecimal} so mixed fact types (Integer,
 * Long, Double, BigDecimal, even numeric Strings) compare correctly and without floating
 * point surprises. A missing or non-numeric fact simply fails the comparison rather than
 * throwing - rules should be total, not blow up on absent facts.
 */
public final class Conditions {

    private Conditions() {
    }

    public static Condition always() {
        return ctx -> true;
    }

    public static Condition eq(String key, Object value) {
        return ctx -> Objects.equals(ctx.get(key), value);
    }

    public static Condition in(String key, Set<?> values) {
        return ctx -> values.contains(ctx.get(key));
    }

    public static Condition gt(String key, Number threshold) {
        return numeric(key, threshold, cmp -> cmp > 0);
    }

    public static Condition gte(String key, Number threshold) {
        return numeric(key, threshold, cmp -> cmp >= 0);
    }

    public static Condition lt(String key, Number threshold) {
        return numeric(key, threshold, cmp -> cmp < 0);
    }

    public static Condition lte(String key, Number threshold) {
        return numeric(key, threshold, cmp -> cmp <= 0);
    }

    private static Condition numeric(String key, Number threshold, java.util.function.IntPredicate cmpTest) {
        BigDecimal bound = new BigDecimal(threshold.toString());
        return ctx -> {
            BigDecimal value = toBigDecimal(ctx.get(key));
            return value != null && cmpTest.test(value.compareTo(bound));
        };
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
