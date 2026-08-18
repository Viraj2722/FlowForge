package com.flowforge.domain.repository;

import com.flowforge.domain.entity.TaskExecution;
import com.flowforge.domain.enums.TaskExecutionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data repository for {@link TaskExecution}.
 */
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    /**
     * Finds tasks whose retry time has arrived, oldest first. This is the query the
     * retry scheduler (Phase 7) polls on a fixed interval; it is backed by the composite
     * index {@code idx_task_exec_retry (status, next_retry_at)} created in Flyway V1.
     *
     * <p>{@link Pageable} bounds how many we pull per tick so one poll can't load a huge
     * backlog into memory at once.
     */
    @Query("""
            select t from TaskExecution t
            where t.status = :status and t.nextRetryAt <= :now
            order by t.nextRetryAt asc
            """)
    List<TaskExecution> findDueForRetry(@Param("status") TaskExecutionStatus status,
                                        @Param("now") Instant now,
                                        Pageable pageable);

    /**
     * Distinct ids of executions that have at least one retryable task now due. The retry
     * scheduler relaunches these executions; the runner then re-runs their due tasks and
     * advances any dependents that become unblocked.
     */
    @Query("""
            select distinct t.workflowExecution.id from TaskExecution t
            where t.status = :status and t.nextRetryAt <= :now
            """)
    List<Long> findExecutionIdsWithDueRetries(@Param("status") TaskExecutionStatus status,
                                              @Param("now") Instant now,
                                              Pageable pageable);

    long countByStatus(TaskExecutionStatus status);
}
