package com.flowforge.engine.handlers;

import com.flowforge.engine.TaskContext;
import com.flowforge.engine.TaskResult;

/**
 * A user-supplied unit of Java logic executed by {@link CustomJavaTaskHandler}.
 *
 * <p>Declared as a {@link FunctionalInterface} so callers (and tests) can supply it as
 * a lambda or method reference. Allowing a checked {@code Exception} keeps arbitrary
 * business code natural to write; {@link CustomJavaTaskHandler} is responsible for
 * catching it and translating it into a {@link TaskResult}.
 */
@FunctionalInterface
public interface CustomTaskAction {

    /**
     * Run the custom logic.
     *
     * @param context the task's immutable inputs
     * @return the result of the work (never null)
     * @throws Exception any failure; the handler decides retryable vs permanent
     */
    TaskResult run(TaskContext context) throws Exception;
}
