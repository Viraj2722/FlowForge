package com.flowforge.api.error;

import com.flowforge.service.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

/**
 * Centralised exception-to-HTTP mapping for every controller.
 *
 * <p>{@code @RestControllerAdvice} registers these handlers globally, so controllers
 * stay free of try/catch and every error comes back as the same {@link ApiError} shape.
 * Mapping each exception type to a deliberate status code is what makes the API
 * predictable:
 * <ul>
 *   <li>validation / bad input  -&gt; 400</li>
 *   <li>not found               -&gt; 404</li>
 *   <li>state conflict / locking / constraint -&gt; 409</li>
 *   <li>anything unexpected     -&gt; 500 (logged, details hidden from the client)</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    /** Bean Validation failures on @Valid request bodies -> 400 with per-field detail. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldViolation> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fields);
    }

    /** Malformed JSON / unparseable body, or a bad enum value in a query param. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadRequestBody(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request: " + rootMessage(ex), req, null);
    }

    /** Business/argument violations surfaced by the service or mapper. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    /** Bad login credentials (thrown by the AuthenticationManager in the login endpoint). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        // Don't leak whether it was the username or the password that was wrong.
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", req, null);
    }

    /** Wrong-state operations (e.g. triggering a non-ACTIVE workflow) -> 409 Conflict. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    /** Concurrent update lost the optimistic-lock race, or a DB constraint was violated. */
    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> handleConflict(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT,
                "The resource was modified or violates a constraint; please retry", req, null);
    }

    /** Last-resort handler: log the real cause, return a generic 500 (no internals leaked). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception for {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req,
                                           List<ApiError.FieldViolation> fields) {
        ApiError body = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI(), fields);
        return ResponseEntity.status(status).body(body);
    }

    private String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
