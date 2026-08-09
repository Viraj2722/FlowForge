package com.flowforge.engine.handlers;

import com.flowforge.engine.TaskContext;
import com.flowforge.engine.TaskHandler;
import com.flowforge.engine.TaskResult;
import com.flowforge.engine.model.TaskType;

import java.util.Map;

/**
 * Handles {@link TaskType#WEBHOOK}: simulates an outbound HTTP call.
 *
 * <p>This handler is the clearest example of the retryable/permanent distinction,
 * mapped from an HTTP status code (supplied via the {@code "simulateStatus"} param):
 * <ul>
 *   <li><b>2xx</b> -&gt; success.</li>
 *   <li><b>408, 429, 5xx</b> -&gt; retryable (server overloaded / timed out — trying
 *       again later may work).</li>
 *   <li><b>other 4xx</b> -&gt; permanent (bad request / unauthorized — the same call
 *       will always fail, so dead-letter it).</li>
 * </ul>
 * Missing {@code "url"} is a permanent validation failure.
 *
 * <p>Stateless and therefore thread-safe.
 */
public class WebhookTaskHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TaskType.WEBHOOK;
    }

    @Override
    public TaskResult handle(TaskContext context) {
        String url = context.stringParam("url").orElse(null);
        if (url == null || url.isBlank()) {
            return TaskResult.permanentFailure(
                    "Missing required 'url'", new IllegalArgumentException("url is required"));
        }

        int status = parseStatus(context.stringParam("simulateStatus").orElse("200"));
        Map<String, Object> output = Map.of("httpStatus", status, "url", url);

        if (status >= 200 && status < 300) {
            return TaskResult.success("Webhook POST " + url + " -> " + status, output);
        }
        if (isRetryable(status)) {
            return TaskResult.retryableFailure(
                    "Webhook transient error " + status + " for " + url,
                    new RuntimeException("HTTP " + status));
        }
        return TaskResult.permanentFailure(
                "Webhook permanent error " + status + " for " + url,
                new RuntimeException("HTTP " + status));
    }

    private static boolean isRetryable(int status) {
        return status == 408      // Request Timeout
                || status == 429  // Too Many Requests
                || status >= 500; // Server errors
    }

    private static int parseStatus(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            // A malformed status is a programming/config error -> treat as permanent
            // by returning a non-retryable client-error code.
            return 400;
        }
    }
}
