package com.flowforge.engine;

import com.flowforge.engine.exception.PermanentTaskException;
import com.flowforge.engine.exception.RetryableTaskException;
import com.flowforge.engine.handlers.ApprovalTaskHandler;
import com.flowforge.engine.handlers.CustomJavaTaskHandler;
import com.flowforge.engine.handlers.CustomTaskAction;
import com.flowforge.engine.handlers.EmailNotificationTaskHandler;
import com.flowforge.engine.handlers.WebhookTaskHandler;
import com.flowforge.engine.model.Outcome;
import com.flowforge.engine.model.Priority;
import com.flowforge.engine.model.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDispatcherTest {

    private TaskDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry(List.of(
                new EmailNotificationTaskHandler(),
                new WebhookTaskHandler(),
                new ApprovalTaskHandler(),
                new CustomJavaTaskHandler()));
        dispatcher = new TaskDispatcher(registry);
    }

    /** Small helper to build a first-attempt context. */
    private TaskContext ctx(TaskType type, Map<String, Object> params) {
        return new TaskContext("task-1", type, Priority.MEDIUM, 1, params, "corr-1");
    }

    @Nested
    class Email {
        @Test
        void succeedsWithRecipient() {
            TaskResult r = dispatcher.dispatch(ctx(TaskType.EMAIL, Map.of("to", "a@b.com")));
            assertThat(r.outcome()).isEqualTo(Outcome.SUCCEEDED);
        }

        @Test
        void missingRecipientIsPermanentFailure() {
            TaskResult r = dispatcher.dispatch(ctx(TaskType.EMAIL, Map.of()));
            assertThat(r.outcome()).isEqualTo(Outcome.PERMANENT_FAILURE);
        }

        @Test
        void transientMailErrorIsRetryable() {
            TaskResult r = dispatcher.dispatch(
                    ctx(TaskType.EMAIL, Map.of("to", "a@b.com", "simulate", "transient")));
            assertThat(r.outcome()).isEqualTo(Outcome.RETRYABLE_FAILURE);
        }
    }

    @Nested
    class Webhook {
        @Test
        void http200Succeeds() {
            TaskResult r = dispatcher.dispatch(
                    ctx(TaskType.WEBHOOK, Map.of("url", "http://x", "simulateStatus", "200")));
            assertThat(r.outcome()).isEqualTo(Outcome.SUCCEEDED);
            assertThat(r.output()).containsEntry("httpStatus", 200);
        }

        @Test
        void http503IsRetryable() {
            TaskResult r = dispatcher.dispatch(
                    ctx(TaskType.WEBHOOK, Map.of("url", "http://x", "simulateStatus", "503")));
            assertThat(r.outcome()).isEqualTo(Outcome.RETRYABLE_FAILURE);
        }

        @Test
        void http404IsPermanent() {
            TaskResult r = dispatcher.dispatch(
                    ctx(TaskType.WEBHOOK, Map.of("url", "http://x", "simulateStatus", "404")));
            assertThat(r.outcome()).isEqualTo(Outcome.PERMANENT_FAILURE);
        }

        @Test
        void http429TooManyRequestsIsRetryable() {
            TaskResult r = dispatcher.dispatch(
                    ctx(TaskType.WEBHOOK, Map.of("url", "http://x", "simulateStatus", "429")));
            assertThat(r.outcome()).isEqualTo(Outcome.RETRYABLE_FAILURE);
        }
    }

    @Nested
    class Approval {
        @Test
        void noDecisionIsPending() {
            TaskResult r = dispatcher.dispatch(ctx(TaskType.APPROVAL, Map.of()));
            assertThat(r.outcome()).isEqualTo(Outcome.PENDING);
        }

        @Test
        void approvedSucceeds() {
            TaskResult r = dispatcher.dispatch(ctx(TaskType.APPROVAL, Map.of("decision", "APPROVED")));
            assertThat(r.outcome()).isEqualTo(Outcome.SUCCEEDED);
        }

        @Test
        void rejectedIsPermanentNotRetryable() {
            TaskResult r = dispatcher.dispatch(ctx(TaskType.APPROVAL, Map.of("decision", "REJECTED")));
            assertThat(r.outcome()).isEqualTo(Outcome.PERMANENT_FAILURE);
        }
    }

    @Nested
    class Custom {
        @Test
        void runsSuppliedAction() {
            CustomTaskAction action = c -> TaskResult.success("did it");
            TaskResult r = dispatcher.dispatch(
                    ctx(TaskType.CUSTOM, Map.of(CustomJavaTaskHandler.ACTION_PARAM, action)));
            assertThat(r.outcome()).isEqualTo(Outcome.SUCCEEDED);
            assertThat(r.message()).isEqualTo("did it");
        }

        @Test
        void missingActionIsPermanentFailure() {
            TaskResult r = dispatcher.dispatch(ctx(TaskType.CUSTOM, Map.of()));
            assertThat(r.outcome()).isEqualTo(Outcome.PERMANENT_FAILURE);
        }

        @Test
        void retryableExceptionFromActionMapsToRetryable() {
            CustomTaskAction action = c -> { throw new RetryableTaskException("try again"); };
            TaskResult r = dispatcher.dispatch(
                    ctx(TaskType.CUSTOM, Map.of(CustomJavaTaskHandler.ACTION_PARAM, action)));
            assertThat(r.outcome()).isEqualTo(Outcome.RETRYABLE_FAILURE);
        }

        @Test
        void permanentExceptionFromActionMapsToPermanent() {
            CustomTaskAction action = c -> { throw new PermanentTaskException("never"); };
            TaskResult r = dispatcher.dispatch(
                    ctx(TaskType.CUSTOM, Map.of(CustomJavaTaskHandler.ACTION_PARAM, action)));
            assertThat(r.outcome()).isEqualTo(Outcome.PERMANENT_FAILURE);
        }

        @Test
        void unexpectedExceptionDefaultsToRetryable() {
            CustomTaskAction action = c -> { throw new RuntimeException("boom"); };
            TaskResult r = dispatcher.dispatch(
                    ctx(TaskType.CUSTOM, Map.of(CustomJavaTaskHandler.ACTION_PARAM, action)));
            assertThat(r.outcome()).isEqualTo(Outcome.RETRYABLE_FAILURE);
        }
    }

    @Test
    void dispatcherTranslatesHandlerThrownExceptions() {
        // A handler that throws instead of returning; dispatcher must normalise it.
        TaskHandler throwing = new TaskHandler() {
            @Override public TaskType type() { return TaskType.EMAIL; }
            @Override public TaskResult handle(TaskContext context) {
                throw new PermanentTaskException("bad config");
            }
        };
        TaskDispatcher d = new TaskDispatcher(new TaskHandlerRegistry(List.of(throwing)));

        TaskResult r = d.dispatch(ctx(TaskType.EMAIL, Map.of("to", "a@b.com")));
        assertThat(r.outcome()).isEqualTo(Outcome.PERMANENT_FAILURE);
        assertThat(r.error()).isInstanceOf(PermanentTaskException.class);
    }
}
