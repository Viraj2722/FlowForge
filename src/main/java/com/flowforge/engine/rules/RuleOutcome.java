package com.flowforge.engine.rules;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The result a matched {@link Rule} produces - a named decision plus optional attributes
 * (e.g. decision {@code SENIOR_APPROVER}, attributes {@code {"reason":"high value"}}).
 */
public record RuleOutcome(String decision, Map<String, Object> attributes) {

    public RuleOutcome {
        attributes = attributes == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    public static RuleOutcome of(String decision) {
        return new RuleOutcome(decision, Map.of());
    }

    public static RuleOutcome of(String decision, Map<String, Object> attributes) {
        return new RuleOutcome(decision, attributes);
    }
}
