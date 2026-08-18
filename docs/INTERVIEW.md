# FlowForge — Interview Q&A Bank

Questions an interviewer can ask about this codebase, with short, defensible answers
grounded in the actual implementation. Skim the code paths in parentheses.

## Architecture & design

**Q: Walk me through the architecture.**
Framework-free engine core (plain Java: Strategy handlers, dispatcher, retry policy, rule
engine) with Spring/JPA/web as adapters around it. Requests → controllers (DTOs, validation)
→ `@Transactional` services → engine + persistence. Reporting reads bypass JPA and use plain
JDBC. (`com.flowforge.engine.*` vs the rest.)

**Q: Why keep the core framework-free?**
Testability (unit tests run in ms, no Spring context), and it enforces clean dependencies —
the domain doesn't depend on the framework (Ports & Adapters). To switch frameworks I'd
touch only the wiring config (`ExecutionEngineConfig`), not the engine.

**Q: Which design patterns did you use and why?**
Strategy (`TaskHandler`, `Condition`), Factory/Registry (`TaskHandlerRegistry`, O(1) enum
lookup instead of a `switch`), Template-ish orchestration (`WorkflowRunner`), Builder
(JWT/claims), Observer (Spring events → audit). Each replaces branching or coupling with
extension points (Open/Closed).

## Java core

**Q: Why records for `TaskContext`/`TaskResult`?**
Immutable value objects with free `equals/hashCode/toString`. I defensively copy the incoming
`Map` in the canonical constructor so callers can't mutate internal state (Effective Java
Item 50).

**Q: Why `Priority.weight()` instead of `ordinal()`?**
`ordinal()` silently changes if constants are reordered; an explicit weight is stable.

**Q: Checked vs unchecked exceptions here?**
Engine exceptions are unchecked so they don't leak `throws` through a functional handler API;
the dispatcher normalises both thrown exceptions and returned failures into one `TaskResult`.

## Concurrency

**Q: Walk me through the concurrent execution.**
`WorkflowRunner` topologically sorts the task DAG (Kahn), then builds one `CompletableFuture`
per task: `allOf(predecessorFutures).thenApplyAsync(runOrCancel, pool)`. Independent tasks run
in parallel on a bounded `ThreadPoolExecutor`; a task starts only after all predecessors
finish and is cancelled if any failed. (`WorkflowRunner.run`.)

**Q: Why a bounded pool and bounded queue?**
The DB/downstream services are the scarce resource. Unbounded threads/queue risk OOM and
overload. A bounded queue + `CallerRunsPolicy` gives back-pressure instead of dropping work.

**Q: Is the handler registry thread-safe?**
Yes — built once in the constructor into an `EnumMap`, never mutated, so it's effectively
immutable and shared safely with no locks.

**Q: How do concurrent DB updates stay correct?**
Each task attempt runs in its own transaction and updates its own `task_executions` row, so
there's no contention. `@Version` optimistic locking catches the rare concurrent update.

**Q: Graceful shutdown?**
`@PreDestroy` calls `shutdown()` then `awaitTermination`; only `shutdownNow()` as a last
resort. So a redeploy drains in-flight tasks. (`TaskWorkerPool`.)

**Q: Why `ConcurrentHashMap.newKeySet()` in the launcher?**
To de-duplicate in-flight executions when the scheduler and a manual `/start` fire together —
many threads may call `launch`, so it must be a concurrent set.

**Q: I/O-bound vs CPU-bound?**
Tasks are I/O-bound (network waits), so concurrency scales throughput well (~6× at pool 8 in
the benchmark). CPU-bound work would only scale to core count.

## Spring

**Q: What does `@SpringBootApplication` bundle?**
`@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`.

**Q: `@Transactional` — where and why in the service layer?**
Spring wraps the method in a proxy that opens a transaction (and Hibernate persistence
context) before and commits after (rollback on runtime exception). I map entities → DTOs
*inside* the boundary so lazy loading happens while the context is open — required with
`open-in-view=false`.

**Q: Self-invocation caveat?**
Calling a `@Transactional` method on `this` bypasses the proxy. That's why `WorkflowRunner`
(not transactional) delegates transactional work to separate beans (`ExecutionStateService`,
`TaskExecutionWorker`).

**Q: `@TransactionalEventListener(AFTER_COMMIT)` vs `@EventListener`?**
AFTER_COMMIT fires only if the publishing tx committed, so audit never records rolled-back
changes. It runs with no active tx, so `AuditService.record` uses `REQUIRES_NEW`.

## JPA / Hibernate

**Q: `ddl-auto=validate` — what does it check, and why not `update`?**
It verifies entity mappings match the real columns and fails fast on drift; it never alters
the schema. Auto-`update` can silently drop/alter columns, has no data migration or rollback
story, and isn't reviewable — Flyway owns DDL instead.

**Q: The N+1 problem — where could it bite and how do you fix it?**
Iterating workflows and touching lazy `steps` would fire N extra queries. Fixes:
`findWithStepsById` uses `JOIN FETCH`; `findByUsername` uses `@EntityGraph`; list endpoints
return summaries without steps. Two tools, different trade-offs.

**Q: Owning vs inverse side?**
The `@ManyToOne` side holds the FK (owning); the `@OneToMany(mappedBy=…)` is inverse. Helper
methods (`addStep`) keep both sides consistent.

**Q: Optimistic locking?**
`@Version` column; Hibernate adds `WHERE version = ?` to updates and bumps it. A stale update
affects 0 rows → `OptimisticLockingFailureException` → mapped to HTTP 409. (Proven in
`TransactionIT`.)

**Q: Cascade & orphan removal?**
`Workflow` → steps is `cascade=ALL, orphanRemoval=true`: steps live and die with the workflow;
removing a step from the collection deletes its row.

**Q: How is JSONB mapped?**
`@JdbcTypeCode(SqlTypes.JSON)` on a `String` field (raw JSON), keeping the entity decoupled
from a specific POJO shape.

## Transactions & data

**Q: Show a real transactional boundary.**
`WorkflowService.create` persists the workflow + all steps + dependency edges in one
transaction; any failure rolls the whole thing back — no half-created workflow.

**Q: Why JDBC for reporting?**
Aggregates (`GROUP BY`, `FILTER`, `EXTRACT(EPOCH …)`) have no entity to hydrate; I want the DB
to aggregate and return flat rows, not load managed entities. `JdbcTemplate` handles
connection/statement/resultset lifecycle + parameter binding (no SQL injection).

**Q: Why no `retry_queue` table but a `dead_letter_tasks` table?**
Retry state (attempt/next_retry_at) is naturally columns on `task_executions` — a queue table
would duplicate it. Dead letters have a different lifecycle (manual inspection + replay) and
retention, and I want to scan them without touching the hot table — that justifies a table.

**Q: What does the `(status, next_retry_at)` index do?**
Powers the scheduler's hot poll `WHERE status='RETRYABLE_FAILURE' AND next_retry_at <= now()`
without a full scan. Verify with `EXPLAIN ANALYZE`.

## Security

**Q: authN vs authZ here?**
Authentication = `AppUserDetailsService` + BCrypt verifying the password at `/auth/login`.
Authorization = URL role rules in the stateless filter chain. Cleanly separate.

**Q: Why stateless JWT and why CSRF disabled?**
No server session — the signed JWT is the whole identity, so any instance validates any
token. CSRF protects cookie-based sessions; with bearer tokens there's no ambient credential
to forge, so it's disabled.

**Q: How are roles enforced?**
JWT carries a `roles` claim; a `JwtAuthenticationConverter` maps it to `ROLE_*` authorities;
URL rules use `hasRole`/`hasAnyRole`. Bad creds → 401; insufficient role → 403 (both tested).

**Q: Password storage?**
BCrypt (adaptive, salted). Plaintext never stored or logged.

## Testing

**Q: Unit vs integration split?**
Pure unit tests for the framework-free core; integration tests against real PostgreSQL for
persistence/web/security; a Testcontainers variant for hermetic CI. MockMvc drives the full
web + security stack.

**Q: Why Testcontainers over an in-memory DB?**
The schema uses Postgres features (JSONB, `FILTER`, timestamptz). H2 would test against a
different database. Testcontainers runs the *real* Postgres, throwaway per run.

## Spring Boot 4 specifics (things that changed)

- Auto-config is **modularized**: Flyway needs `spring-boot-starter-flyway`, not raw
  `flyway-core`.
- **Jackson 3**: core moved to the `tools.jackson.*` package with unchecked exceptions.
- The MockMvc test slice isn't on `starter-test`; build MockMvc from the web context.
- Spring Security 7 lambda DSL; JWT via `NimbusJwtEncoder/Decoder`.

## Rapid-fire

- **REST status codes used:** 200/201(+Location)/202/204/400/401/403/404/409/500.
- **Why 202 for trigger/start?** Work accepted, processed asynchronously.
- **What's immutable in the design?** value records, `RetryPolicy`, `RuleEngine`, the handler
  registry.
- **Biggest limitation?** single-instance scheduler (needs distributed locking to scale out).
