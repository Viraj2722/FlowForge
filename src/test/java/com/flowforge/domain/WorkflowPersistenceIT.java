package com.flowforge.domain;

import com.flowforge.domain.entity.Workflow;
import com.flowforge.domain.entity.WorkflowStep;
import com.flowforge.domain.repository.WorkflowRepository;
import com.flowforge.engine.model.Priority;
import com.flowforge.engine.model.TaskType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA/Hibernate integration test against real PostgreSQL.
 *
 * <p>Because it uses {@code @SpringBootTest}, the full context starts - which means
 * Hibernate runs {@code ddl-auto=validate}. If ANY entity mapping disagreed with the
 * Flyway schema (wrong column name, type, nullability), the context would fail to load
 * and this test would error. So a green run here also certifies "entities match schema".
 *
 * <p>Gated on {@code DB_NAME} and {@code @Transactional} (rolls back), same as the
 * Phase 2 reporting IT.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = ".+")
class WorkflowPersistenceIT {

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private EntityManager em;

    @Test
    void savingWorkflowCascadesToStepsAndFetchJoinLoadsThemInOrder() {
        Workflow wf = new Workflow("Onboarding", "New customer onboarding", Priority.HIGH);
        // Add steps out of order to prove @OrderBy sorts them on load.
        wf.addStep(new WorkflowStep("notify", TaskType.EMAIL, 2, "{\"to\":\"a@b.com\"}"));
        wf.addStep(new WorkflowStep("call-crm", TaskType.WEBHOOK, 1, "{\"url\":\"http://crm\"}"));

        Workflow saved = workflowRepository.save(wf); // cascade persists both steps
        Long id = saved.getId();
        assertThat(id).isNotNull();

        // Detach everything so the next read really hits the DB (not the 1st-level cache).
        em.flush();
        em.clear();

        Workflow reloaded = workflowRepository.findWithStepsById(id).orElseThrow();
        assertThat(reloaded.getSteps()).hasSize(2);
        // @OrderBy("stepOrder ASC") => call-crm (order 1) before notify (order 2)
        assertThat(reloaded.getSteps().get(0).getName()).isEqualTo("call-crm");
        assertThat(reloaded.getSteps().get(1).getName()).isEqualTo("notify");
    }

    @Test
    void versionColumnIncrementsOnUpdate_optimisticLocking() {
        Workflow wf = new Workflow("Billing", "Monthly billing run", Priority.MEDIUM);
        Workflow saved = workflowRepository.save(wf);
        em.flush();
        assertThat(saved.getVersion()).isZero(); // fresh insert -> version 0

        saved.setDescription("Monthly billing run (updated)");
        workflowRepository.save(saved);
        em.flush(); // UPDATE fires here, Hibernate bumps @Version

        assertThat(saved.getVersion()).isEqualTo(1);
    }

    @Test
    void stepDependencyDagIsPersistedAndReloaded() {
        Workflow wf = new Workflow("DAG demo", null, Priority.LOW);
        WorkflowStep first = new WorkflowStep("first", TaskType.CUSTOM, 1, null);
        WorkflowStep second = new WorkflowStep("second", TaskType.CUSTOM, 2, null);
        second.addDependency(first); // second depends on first
        wf.addStep(first);
        wf.addStep(second);

        Long id = workflowRepository.save(wf).getId();
        em.flush();
        em.clear();

        Workflow reloaded = workflowRepository.findWithStepsById(id).orElseThrow();
        WorkflowStep reloadedSecond = reloaded.getSteps().stream()
                .filter(s -> s.getName().equals("second"))
                .findFirst().orElseThrow();
        // Lazy dependency set is loaded on access (session still open inside the tx).
        assertThat(reloadedSecond.getDependencies()).hasSize(1);
        assertThat(reloadedSecond.getDependencies().iterator().next().getName()).isEqualTo("first");
    }
}
