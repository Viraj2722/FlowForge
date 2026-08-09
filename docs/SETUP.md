# FlowForge — Manual Setup Guide

This file lists **everything you must do by hand** (outside the code) to run FlowForge.
Each phase appends its own requirements here. Do the steps for the phases you've reached.

---

## Phase 1 — Toolchain

### 1. Use JDK 21 (you have it installed, but your system default is JDK 17)

- **What:** point `JAVA_HOME` at `C:\Program Files\Java\jdk-21.0.11` and put `%JAVA_HOME%\bin` first on `PATH`.
- **Why:** the project compiles against Java 21 language features; a Java 17 `mvn` will fail.
- **Where:** Windows → *Edit the system environment variables* → **Environment Variables**.
- **Verify:** open a **new** terminal:
  ```bash
  java -version    # expect 21.0.11
  mvn -version     # "Java version: 21..."
  ```
- **IntelliJ:** *File → Project Structure → Project SDK → 21*, and *Settings → Build Tools → Maven → Importing → JDK for importer → 21*.
- **Required:** now.

---

## Phase 2 — PostgreSQL database

Your PostgreSQL 18 server is already installed and **running on `localhost:5432`**
(verified). You only need to create a database and a dedicated user for FlowForge.

### 2. Create the `flowforge` database and user

- **What:** a database named `flowforge` and a login role `flowforge` that owns it.
- **Why:** the app connects as a **least-privilege** application user (not the `postgres`
  superuser). Flyway will create all tables inside this database on first startup.
- **Where:** a terminal, using `psql`. You will be prompted for the **postgres**
  superuser password (the one you set when installing PostgreSQL).
- **Exact steps** (run in a terminal):
  ```bash
  psql -U postgres -h localhost -c "CREATE USER flowforge WITH PASSWORD 'choose_a_strong_password';"
  psql -U postgres -h localhost -c "CREATE DATABASE flowforge OWNER flowforge;"
  psql -U postgres -h localhost -c "GRANT ALL PRIVILEGES ON DATABASE flowforge TO flowforge;"
  ```
- **What value to use:** pick any strong password for `flowforge`; you'll put the same
  value into `DB_PASSWORD` below. Do **not** reuse the postgres superuser password.
- **Verify:**
  ```bash
  psql -U flowforge -h localhost -d flowforge -c "\conninfo"
  ```
  It should say you are connected to database `flowforge` as user `flowforge`.
- **Required:** now (Phase 2 needs it).

### 3. Provide the DB credentials to the app as environment variables

- **What:** set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.
- **Why:** credentials are **never** hardcoded or committed; the app reads them from the
  environment (see `application.yml`). `.env.example` documents them.
- **Where (recommended):** IntelliJ → *Run/Debug Configurations* → your Spring Boot config
  → **Environment variables** field. For terminal runs, `export` them first.
- **Values:**
  ```
  DB_HOST=localhost
  DB_PORT=5432
  DB_NAME=flowforge
  DB_USERNAME=flowforge
  DB_PASSWORD=the_password_you_chose_above
  ```
- **Verify (applies the migrations):**
  ```bash
  mvn -q spring-boot:run
  ```
  Watch the logs for Flyway lines like `Migrating schema "public" to version "1 - baseline schema"`
  and `Successfully applied 2 migrations`. Then confirm the tables exist:
  ```bash
  psql -U flowforge -h localhost -d flowforge -c "\dt"
  ```
  You should see `workflows`, `task_executions`, `dead_letter_tasks`, `roles`, etc., plus
  Flyway's own `flyway_schema_history`. Stop the app with Ctrl+C.
- **Required:** now.

### 4. (Optional) Run the JDBC reporting integration test

Once steps 2–3 are done and `DB_NAME` is set in your environment, you can run the
DB-backed test (it's skipped otherwise, and rolls back all its data):
```bash
mvn -Dtest=ReportingJdbcDaoIT -DfailIfNoTests=false test
```
- **Required:** optional — nice confidence check.

---

_Docker is NOT required yet. It first appears in Phase 10 (Testcontainers) and Phase 12
(Docker Compose)._
