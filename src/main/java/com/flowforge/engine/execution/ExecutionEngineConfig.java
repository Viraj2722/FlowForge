package com.flowforge.engine.execution;

import com.flowforge.engine.TaskDispatcher;
import com.flowforge.engine.TaskHandler;
import com.flowforge.engine.TaskHandlerRegistry;
import com.flowforge.engine.handlers.ApprovalTaskHandler;
import com.flowforge.engine.handlers.CustomJavaTaskHandler;
import com.flowforge.engine.handlers.EmailNotificationTaskHandler;
import com.flowforge.engine.handlers.WebhookTaskHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the framework-free engine core into the Spring context.
 *
 * <p>Deliberate design point: the {@link TaskHandler} implementations do NOT depend on
 * Spring (no {@code @Component}). We keep the domain/engine framework-agnostic and do the
 * wiring here, at the edge. Swapping frameworks would touch only this config, not the
 * engine. The {@link TaskHandlerRegistry} also fails fast at startup if two handlers
 * claim the same task type.
 */
@Configuration
public class ExecutionEngineConfig {

    @Bean
    public TaskHandlerRegistry taskHandlerRegistry() {
        List<TaskHandler> handlers = List.of(
                new EmailNotificationTaskHandler(),
                new WebhookTaskHandler(),
                new ApprovalTaskHandler(),
                new CustomJavaTaskHandler());
        return new TaskHandlerRegistry(handlers);
    }

    @Bean
    public TaskDispatcher taskDispatcher(TaskHandlerRegistry registry) {
        return new TaskDispatcher(registry);
    }
}
