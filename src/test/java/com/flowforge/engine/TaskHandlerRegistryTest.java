package com.flowforge.engine;

import com.flowforge.engine.handlers.ApprovalTaskHandler;
import com.flowforge.engine.handlers.CustomJavaTaskHandler;
import com.flowforge.engine.handlers.EmailNotificationTaskHandler;
import com.flowforge.engine.handlers.WebhookTaskHandler;
import com.flowforge.engine.model.TaskType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskHandlerRegistryTest {

    private TaskHandlerRegistry newRegistry() {
        return new TaskHandlerRegistry(List.of(
                new EmailNotificationTaskHandler(),
                new WebhookTaskHandler(),
                new ApprovalTaskHandler(),
                new CustomJavaTaskHandler()));
    }

    @Test
    void resolvesEachTypeToItsHandler() {
        TaskHandlerRegistry registry = newRegistry();

        assertThat(registry.resolve(TaskType.EMAIL)).isInstanceOf(EmailNotificationTaskHandler.class);
        assertThat(registry.resolve(TaskType.WEBHOOK)).isInstanceOf(WebhookTaskHandler.class);
        assertThat(registry.resolve(TaskType.APPROVAL)).isInstanceOf(ApprovalTaskHandler.class);
        assertThat(registry.resolve(TaskType.CUSTOM)).isInstanceOf(CustomJavaTaskHandler.class);
        assertThat(registry.size()).isEqualTo(4);
    }

    @Test
    void supportsReportsCoverage() {
        TaskHandlerRegistry registry = newRegistry();

        for (TaskType type : TaskType.values()) {
            assertThat(registry.supports(type))
                    .as("every TaskType should have a handler: " + type)
                    .isTrue();
        }
    }

    @Test
    void duplicateHandlerForSameTypeIsRejected() {
        assertThatThrownBy(() -> new TaskHandlerRegistry(List.of(
                new EmailNotificationTaskHandler(),
                new EmailNotificationTaskHandler())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate handler");
    }

    @Test
    void resolvingAnUnregisteredTypeThrows() {
        // Registry with only EMAIL registered
        TaskHandlerRegistry registry = new TaskHandlerRegistry(List.of(new EmailNotificationTaskHandler()));

        assertThatThrownBy(() -> registry.resolve(TaskType.WEBHOOK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No handler registered");
    }
}
