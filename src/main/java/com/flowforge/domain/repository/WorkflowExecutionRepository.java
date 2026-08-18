package com.flowforge.domain.repository;

import com.flowforge.domain.entity.WorkflowExecution;
import com.flowforge.domain.enums.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link WorkflowExecution}.
 */
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {

    Optional<WorkflowExecution> findByCorrelationId(String correlationId);

    Page<WorkflowExecution> findByStatus(ExecutionStatus status, Pageable pageable);

    /**
     * Returns just the ids of executions in a given status. The scheduler uses this to
     * find PENDING executions to start - selecting only ids keeps the poll cheap (no
     * entity hydration) and {@link Pageable} bounds the batch size.
     */
    @Query("select e.id from WorkflowExecution e where e.status = :status order by e.id")
    List<Long> findIdsByStatus(@Param("status") ExecutionStatus status, Pageable pageable);
}
