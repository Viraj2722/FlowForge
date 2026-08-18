package com.flowforge.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Class-level Bean Validation constraint for a create-workflow request: every step must
 * have a distinct {@code stepOrder}, and every {@code dependsOn} entry must reference a
 * step that actually exists in the request.
 *
 * <p>This is a <b>custom cross-field constraint</b> - the built-in annotations validate
 * one field at a time, but this rule spans the whole {@code steps} collection, so it has
 * to live at the type level with its own {@link UniqueStepOrdersValidator}. Doing it here
 * means bad input is rejected at the edge with a clean 400 before any service/DB work.
 */
@Documented
@Constraint(validatedBy = UniqueStepOrdersValidator.class)
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface UniqueStepOrders {

    String message() default "steps must have unique stepOrder values and dependsOn must reference existing steps";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
