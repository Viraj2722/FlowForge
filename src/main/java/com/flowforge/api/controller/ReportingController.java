package com.flowforge.api.controller;

import com.flowforge.reporting.ReportingJdbcDao;
import com.flowforge.reporting.dto.DashboardSummary;
import com.flowforge.reporting.dto.StatusCount;
import com.flowforge.reporting.dto.TaskTypeFailureCount;
import com.flowforge.reporting.dto.WorkflowThroughput;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only reporting endpoints, served by the JDBC reporting DAO (Phase 2) rather than
 * JPA - these are aggregate queries, not entity reads.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingJdbcDao reportingDao;

    public ReportingController(ReportingJdbcDao reportingDao) {
        this.reportingDao = reportingDao;
    }

    @GetMapping("/dashboard")
    public DashboardSummary dashboard() {
        return reportingDao.dashboardSummary();
    }

    @GetMapping("/executions-by-status")
    public List<StatusCount> executionsByStatus() {
        return reportingDao.executionCountsByStatus();
    }

    @GetMapping("/workflow-throughput")
    public List<WorkflowThroughput> workflowThroughput(
            @RequestParam(defaultValue = "10") int limit) {
        return reportingDao.workflowThroughput(limit);
    }

    @GetMapping("/task-failures")
    public List<TaskTypeFailureCount> taskFailures() {
        return reportingDao.taskFailureBreakdown();
    }
}
