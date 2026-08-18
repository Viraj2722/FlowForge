package com.flowforge.domain.entity;

import com.flowforge.domain.enums.WorkflowStatus;
import com.flowforge.engine.model.Priority;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A workflow <em>definition</em>: the template a user designs. Executions are created
 * from it. This is the aggregate root for its steps.
 *
 * <p>Mapping decisions worth defending:
 * <ul>
 *   <li><b>steps</b> is {@code @OneToMany(cascade = ALL, orphanRemoval = true)} - the
 *       steps' lifecycle is bound to the workflow. Removing a step from the collection
 *       deletes its row (orphan removal); deleting the workflow cascades to its steps.</li>
 *   <li>The collection is {@code LAZY} (default for {@code @OneToMany}) to avoid loading
 *       every step whenever a workflow is fetched. Callers that need steps use the
 *       repository's fetch-join query to avoid the N+1 problem.</li>
 *   <li><b>createdBy</b> is {@code @ManyToOne(LAZY)} - we don't want to load the whole
 *       user just to show a workflow.</li>
 *   <li><b>@Version</b> enables optimistic locking (Phase 5): concurrent updates to the
 *       same workflow are detected instead of silently overwriting each other.</li>
 * </ul>
 */
@Entity
@Table(name = "workflows")
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowStatus status = WorkflowStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<WorkflowStep> steps = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected Workflow() {
    }

    public Workflow(String name, String description, Priority priority) {
        this.name = name;
        this.description = description;
        this.priority = priority;
    }

    /**
     * Adds a step and keeps BOTH sides of the relationship consistent. Managing both
     * sides by hand is required with bidirectional JPA associations - the owning side
     * ({@code WorkflowStep.workflow}) is what actually gets persisted.
     */
    public void addStep(WorkflowStep step) {
        steps.add(step);
        step.setWorkflow(this);
    }

    public void removeStep(WorkflowStep step) {
        steps.remove(step);
        step.setWorkflow(null);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public List<WorkflowStep> getSteps() {
        return steps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Workflow that)) return false;
        // Entities are equal only once both have an assigned id.
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        // Constant hashCode is the safe choice for JPA entities whose id is generated
        // on persist (see Vlad Mihalcea): it keeps the object usable in a HashSet both
        // before and after the id is assigned.
        return getClass().hashCode();
    }
}
