package com.flowforge.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error response body returned for EVERY failed request, so clients always
 * parse the same shape regardless of which error occurred.
 *
 * <p>{@code @JsonInclude(NON_NULL)} keeps the payload tidy - {@code fieldErrors} only
 * appears on validation failures.
 *
 * @param timestamp   when the error was produced
 * @param status      HTTP status code (e.g. 404)
 * @param error       HTTP reason phrase (e.g. "Not Found")
 * @param message     human-readable, safe-to-expose explanation
 * @param path        the request URI that failed
 * @param fieldErrors per-field validation messages, when applicable
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {
    /** A single field-level validation problem. */
    public record FieldViolation(String field, String message) {
    }
}
