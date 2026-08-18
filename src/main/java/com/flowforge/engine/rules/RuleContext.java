package com.flowforge.engine.rules;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable bag of facts a {@link Rule} is evaluated against (e.g. {@code priority},
 * {@code amount}). Same immutability discipline as the task engine's context: defensive
 * copy so callers can't mutate our state after construction.
 */
public record RuleContext(Map<String, Object> facts) {

    public RuleContext {
        facts = facts == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(facts));
    }

    public static RuleContext of(Map<String, Object> facts) {
        return new RuleContext(facts);
    }

    public Object get(String key) {
        return facts.get(key);
    }
}
