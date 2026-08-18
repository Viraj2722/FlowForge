package com.flowforge.domain.entity;

import com.flowforge.engine.model.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One step (task) within a workflow definition.
 *
 * <p>Notable mappings:
 * <ul>
 *   <li><b>workflow</b> is the OWNING side of the workflow&lt;-&gt;steps association
 *       ({@code @ManyToOne} + {@code @JoinColumn}); this is the FK that actually gets
 *       written. {@code Workflow.steps} is the inverse ({@code mappedBy}).</li>
 *   <li><b>parameters</b> is a JSONB column mapped with {@code @JdbcTypeCode(JSON)}. We
 *       keep it as the raw JSON string here (simple, no coupling to a POJO); the engine
 *       parses it into the {@code TaskContext} at run time.</li>
 *   <li><b>dependencies</b> is a self many-to-many over {@code step_dependencies} - the
 *       edges of the workflow's dependency DAG. A step may depend on several others.</li>
 * </ul>
 */
@Entity
@Table(name = "workflow_steps")
public class WorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    private TaskType taskType;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String parameters = "{}";

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    /**
     * Steps this step depends on (its predecessors in the DAG). Self many-to-many via
     * the {@code step_dependencies} join table. LAZY so we don't walk the graph on load.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "step_dependencies",
            joinColumns = @JoinColumn(name = "step_id"),
            inverseJoinColumns = @JoinColumn(name = "depends_on_step_id"))
    private Set<WorkflowStep> dependencies = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkflowStep() {
    }

    public WorkflowStep(String name, TaskType taskType, int stepOrder, String parameters) {
        this.name = name;
        this.taskType = taskType;
        this.stepOrder = stepOrder;
        if (parameters != null && !parameters.isBlank()) {
            this.parameters = parameters;
        }
    }

    public void addDependency(WorkflowStep predecessor) {
        this.dependencies.add(predecessor);
    }

    public Long getId() {
        return id;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(int stepOrder) {
        this.stepOrder = stepOrder;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Set<WorkflowStep> getDependencies() {
        return dependencies;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkflowStep that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
