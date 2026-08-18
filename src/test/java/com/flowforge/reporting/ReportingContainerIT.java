package com.flowforge.reporting;

import com.flowforge.reporting.dto.DashboardSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fully self-contained integration test: it boots a REAL PostgreSQL in a throwaway Docker
 * container, points the app's datasource at it via {@link DynamicPropertySource}, lets
 * Flyway migrate it, and exercises the reporting DAO - all with zero dependence on a
 * hand-configured local database.
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)} makes the whole class skip when
 * Docker isn't available, so the build stays green on machines/CI without Docker. On a
 * machine with Docker Desktop running, it executes end-to-end.
 *
 * <p>This is the direction all the {@code *IT} tests would move for CI: the env-var gate
 * ({@code DB_NAME}) is a convenience for local dev against your own Postgres; this pattern
 * is what makes the suite hermetic and reproducible anywhere.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ReportingContainerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // Override whatever the local profile configured; point at the container instead.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ReportingJdbcDao reportingDao;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayMigratesTheContainerAndReportingWorks() {
        // Flyway V1+V2 ran against the fresh container -> the 4 seed roles exist.
        Long roleCount = jdbc.queryForObject("SELECT count(*) FROM roles", Long.class);
        assertThat(roleCount).isEqualTo(4L);

        // Empty schema -> zero workflows.
        DashboardSummary empty = reportingDao.dashboardSummary();
        assertThat(empty.totalWorkflows()).isZero();

        // Insert one and confirm the aggregate query reflects it.
        jdbc.update("INSERT INTO workflows (name, status, priority) VALUES (?, ?, ?)",
                "container-wf", "ACTIVE", "HIGH");
        DashboardSummary after = reportingDao.dashboardSummary();
        assertThat(after.totalWorkflows()).isEqualTo(1);
        assertThat(after.activeWorkflows()).isEqualTo(1);
    }
}
