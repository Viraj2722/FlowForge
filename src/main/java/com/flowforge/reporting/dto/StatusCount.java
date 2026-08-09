package com.flowforge.reporting.dto;

/**
 * A single "status -&gt; count" row from an aggregate query.
 *
 * <p>This is a read-only projection, not an entity: it exists only to carry the shape
 * of a {@code GROUP BY status} result out of the reporting DAO. Modelling report rows
 * as small records (rather than reusing JPA entities) keeps the read side decoupled
 * from the write model - a deliberate CQRS-flavoured choice.
 */
public record StatusCount(String status, long count) {
}
