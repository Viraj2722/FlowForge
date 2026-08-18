# Performance & Profiling

## Benchmark: sequential vs concurrent execution

`ConcurrencyBenchmarkTest` runs the same set of I/O-bound tasks (each simulating ~50 ms of
network latency) one-by-one, then through a thread pool, and reports the speedup.

Run it:
```bash
FLOWFORGE_BENCHMARK=true mvn -Dtest=ConcurrencyBenchmarkTest -DfailIfNoTests=false test
```

Representative result (16 tasks, 50 ms each, pool size 8):
```
sequential:   905 ms
concurrent:   145 ms
speedup   :   6.2x
```

**Why this shape?** FlowForge's tasks (email, webhook) are **I/O-bound** — they spend
their time waiting on the network, not on the CPU. Concurrency lets many wait at once, so
throughput scales with the pool size until the downstream becomes the bottleneck. For
**CPU-bound** work the story is different: parallelism only helps up to the core count,
and past that you just add context-switching overhead. Knowing which regime you're in is
the whole game — that's why the worker pool is bounded rather than unbounded.

## What to measure, and how (IntelliJ-friendly)

| Concern | Tool | How |
|---|---|---|
| CPU hot spots / slow methods | **IntelliJ Profiler** (async-profiler/JFR built in) | *Run → Profile 'FlowForgeApplication'*; read the flame graph. Sampled, low overhead. |
| Allocations / memory | IntelliJ Profiler (allocations) or a **heap dump** | Profile with allocation recording; or `jmap`/*Capture Memory Snapshot* and inspect dominators. |
| Threads / deadlocks / pool saturation | **Thread dump** | *Run tab → camera icon*, or `jstack <pid>`. Look for many `ff-worker-*` threads and queue backlog. |
| GC / JIT / everything, low overhead | **Java Flight Recorder** | `java -XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=ff.jfr -jar target/flowforge-*.jar`, open `ff.jfr` in JDK Mission Control or IntelliJ. |
| Live app metrics | **Actuator + Micrometer** | `GET /actuator/metrics`, `GET /actuator/prometheus`. Custom meters: `flowforge.task.duration`, `flowforge.tasks.completed`, `flowforge.worker.pool.active`, `flowforge.worker.pool.queue.size`. |
| Slow SQL from the app | **Hibernate statistics** | Temporarily set `spring.jpa.properties.hibernate.generate_statistics=true` and log `org.hibernate.stat` to see query counts/times and catch N+1s. |
| Slow SQL in the DB | **PostgreSQL** | `SET log_min_duration_statement = 200;` (log queries > 200 ms), and `EXPLAIN (ANALYZE, BUFFERS) <query>` to read the plan. Confirms the `idx_task_exec_retry` index is used by the scheduler poll. |
| HTTP load | **hey / k6 / JMeter** | e.g. `hey -n 2000 -c 50 -H "Authorization: Bearer <jwt>" http://localhost:8080/api/v1/workflows` |

## Tuning knobs

- Worker pool: `flowforge.engine.core-pool-size`, `max-pool-size`, `queue-capacity`.
- DB pool: `spring.datasource.hikari.maximum-pool-size` — keep it in sensible proportion
  to the worker pool so workers don't starve waiting for connections.
- Scheduler batch/interval: `flowforge.scheduler.batch-size`, `*-interval-ms`.

## A note on the N+1 problem

The list endpoints return summaries (no steps) specifically to avoid N+1 lazy loads, and
`WorkflowRepository.findWithStepsById` uses a JOIN FETCH for the detail view. Turning on
Hibernate statistics is the fastest way to catch a regression here.
