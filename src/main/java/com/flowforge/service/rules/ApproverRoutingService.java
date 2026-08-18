package com.flowforge.service.rules;

import com.flowforge.engine.model.Priority;
import com.flowforge.engine.rules.RuleContext;
import com.flowforge.engine.rules.RuleEngine;
import com.flowforge.engine.rules.RuleOutcome;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Decides which approver an approval should route to, by consulting the approver
 * {@link RuleEngine}. Thin adapter: it turns typed inputs into a {@link RuleContext},
 * asks the engine, and applies a safe default if no rule matched.
 */
@Service
public class ApproverRoutingService {

    private final RuleEngine approverRuleEngine;

    public ApproverRoutingService(RuleEngine approverRuleEngine) {
        this.approverRuleEngine = approverRuleEngine;
    }

    public RuleOutcome route(Priority priority, BigDecimal amount) {
        RuleContext context = RuleContext.of(Map.of(
                ApproverRoutingConfig.PRIORITY, priority,
                ApproverRoutingConfig.AMOUNT, amount));
        return approverRuleEngine.firstMatch(context)
                .orElseGet(() -> RuleOutcome.of("STANDARD_APPROVER", Map.of("reason", "no rule matched")));
    }
}
