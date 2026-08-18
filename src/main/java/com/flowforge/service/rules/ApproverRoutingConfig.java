package com.flowforge.service.rules;

import com.flowforge.engine.model.Priority;
import com.flowforge.engine.rules.Conditions;
import com.flowforge.engine.rules.Rule;
import com.flowforge.engine.rules.RuleEngine;
import com.flowforge.engine.rules.RuleOutcome;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Wires an approval-routing {@link RuleEngine} from declarative rules. This is where the
 * rule engine's power shows: adding or reordering a routing policy is a data change here,
 * not new branching logic scattered through services.
 *
 * <p>Encodes the canonical example plus fallbacks (first match wins, by {@code order}):
 * <pre>
 *   IF priority == CRITICAL, OR (priority == HIGH AND amount > 100000) -> SENIOR_APPROVER
 *   ELSE IF amount > 10000                                             -> MANAGER_APPROVER
 *   ELSE                                                               -> STANDARD_APPROVER
 * </pre>
 */
@Configuration
public class ApproverRoutingConfig {

    public static final String PRIORITY = "priority";
    public static final String AMOUNT = "amount";

    @Bean
    public RuleEngine approverRuleEngine() {
        Rule senior = new Rule(
                "senior-for-critical-or-high-value",
                10,
                Conditions.eq(PRIORITY, Priority.CRITICAL)
                        .or(Conditions.eq(PRIORITY, Priority.HIGH).and(Conditions.gt(AMOUNT, 100_000))),
                RuleOutcome.of("SENIOR_APPROVER", Map.of("reason", "critical priority or high-value HIGH")));

        Rule manager = new Rule(
                "manager-for-mid-value",
                20,
                Conditions.gt(AMOUNT, 10_000),
                RuleOutcome.of("MANAGER_APPROVER", Map.of("reason", "mid-value amount")));

        Rule standard = new Rule(
                "default-standard",
                30,
                Conditions.always(),
                RuleOutcome.of("STANDARD_APPROVER", Map.of("reason", "default")));

        return new RuleEngine(List.of(senior, manager, standard));
    }
}
