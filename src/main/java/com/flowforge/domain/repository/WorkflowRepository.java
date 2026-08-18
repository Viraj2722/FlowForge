package com.flowforge.domain.repository;

import com.flowforge.domain.entity.Workflow;
import com.flowforge.domain.enums.WorkflowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data repository for {@link Workflow}.
 */
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {

    /**
     * Loads a workflow together with its steps in ONE query using a JOIN FETCH.
     *
     * <p>Without this, calling {@code workflow.getSteps()} on a lazily-loaded workflow
     * triggers a second SELECT - and iterating N workflows would fire N extra queries
     * (the classic <b>N+1 problem</b>). The {@code distinct} keyword removes duplicate
     * workflow rows that the JOIN produces when there are multiple steps.
     */
    @Query("select distinct w from Workflow w left join fetch w.steps where w.id = :id")
    Optional<Workflow> findWithStepsById(@Param("id") Long id);

    /** Paginated listing filtered by status - supports the REST list endpoint later. */
    Page<Workflow> findByStatus(WorkflowStatus status, Pageable pageable);
}
