package com.flowforge.engine.rules;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates an ordered set of {@link Rule}s against a {@link RuleContext}.
 *
 * <p>Framework-free, immutable, thread-safe: rules are sorted once at construction and
 * never mutated, so a single engine instance is safely shared. Supports two strategies:
 * <ul>
 *   <li>{@link #firstMatch} - highest-precedence matching rule wins (routing decisions).</li>
 *   <li>{@link #allMatches} - every matching rule fires (e.g. collecting tags).</li>
 * </ul>
 */
public class RuleEngine {

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(Rule::order))
                .toList(); // immutable copy
    }

    public Optional<RuleOutcome> firstMatch(RuleContext context) {
        for (Rule rule : rules) {
            Optional<RuleOutcome> outcome = rule.evaluate(context);
            if (outcome.isPresent()) {
                return outcome;
            }
        }
        return Optional.empty();
    }

    public List<RuleOutcome> allMatches(RuleContext context) {
        return rules.stream()
                .map(rule -> rule.evaluate(context))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public int size() {
        return rules.size();
    }
}
