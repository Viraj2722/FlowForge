package com.flowforge.engine.rules;

import com.flowforge.engine.model.Priority;
import com.flowforge.service.rules.ApproverRoutingConfig;
import com.flowforge.service.rules.ApproverRoutingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure (no Spring) test of the rule engine via the approver-routing rules. Exercises the
 * canonical "HIGH + amount &gt; 100000 -> SENIOR_APPROVER" policy and its fallbacks.
 */
class RuleEngineTest {

    private final ApproverRoutingService routing =
            new ApproverRoutingService(new ApproverRoutingConfig().approverRuleEngine());

    private String decisionFor(Priority priority, String amount) {
        return routing.route(priority, new BigDecimal(amount)).decision();
    }

    @Test
    void highPriorityHighValueRoutesToSenior() {
        assertThat(decisionFor(Priority.HIGH, "200000")).isEqualTo("SENIOR_APPROVER");
    }

    @Test
    void criticalPriorityAlwaysRoutesToSenior() {
        assertThat(decisionFor(Priority.CRITICAL, "0")).isEqualTo("SENIOR_APPROVER");
    }

    @Test
    void highPriorityButLowAmountDoesNotRouteToSenior() {
        // HIGH but amount not > 100000, and not > 10000 -> default
        assertThat(decisionFor(Priority.HIGH, "5000")).isEqualTo("STANDARD_APPROVER");
    }

    @Test
    void midValueRoutesToManager() {
        assertThat(decisionFor(Priority.LOW, "50000")).isEqualTo("MANAGER_APPROVER");
    }

    @Test
    void lowValueRoutesToStandard() {
        assertThat(decisionFor(Priority.LOW, "100")).isEqualTo("STANDARD_APPROVER");
    }

    @Test
    void firstMatchOrderingIsRespected() {
        // amount 200000 matches both the senior rule (HIGH) and the manager rule (>10000);
        // the lower-order senior rule must win.
        RuleOutcome outcome = new ApproverRoutingConfig().approverRuleEngine()
                .firstMatch(RuleContext.of(java.util.Map.of(
                        ApproverRoutingConfig.PRIORITY, Priority.HIGH,
                        ApproverRoutingConfig.AMOUNT, new BigDecimal("200000"))))
                .orElseThrow();
        assertThat(outcome.decision()).isEqualTo("SENIOR_APPROVER");
    }
}
