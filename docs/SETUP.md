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
  psql -U postgres -h localhost -d flowforge -c "ALTER SCHEMA public OWNER TO flowforge;"
  ```
  > **PostgreSQL 15+ note:** the last line is essential. Since PG 15, owning a database
  > does NOT grant rights on its `public` schema (it's still owned by `postgres`, and
  > `CREATE` was revoked from `PUBLIC`). Without transferring schema ownership, Flyway
  > fails with `permission denied for schema public` when it runs `CREATE TABLE`.
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

## Phase 9 — Security (JWT) & bootstrap admin

The app is now secured. For **local dev nothing extra is required** — the git-ignored
`application-local.yml` already provides a dev JWT secret, and a bootstrap ADMIN user is
created on first startup.

### 5. JWT signing secret (required in real environments, auto in local)

- **What:** `FLOWFORGE_JWT_SECRET` — a random string of **at least 32 bytes** used to
  sign/verify HS256 JWTs.
- **Why:** tokens are signed with this secret; anyone who knows it can forge tokens.
- **Where:** environment variable (prod/Docker). Locally it's already set in
  `application-local.yml`.
- **How to generate a good one:**
  ```bash
  openssl rand -base64 48
  ```
- **Verify:** the app fails to start with a clear message if the secret is missing/too short.
- **Required:** now only if you run with a non-local profile; otherwise later (Docker).

### 6. Bootstrap admin login

- **What:** on first startup FlowForge creates an `admin` user (password from
  `FLOWFORGE_ADMIN_PASSWORD`, dev default `admin12345`).
- **Why:** you need one account to log in and create others.
- **How to log in and call the API:**
  ```bash
  curl -s -XPOST localhost:8080/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin12345\"}"
  ```
  Copy the `token`, then call protected endpoints with it:
  ```bash
  curl -s localhost:8080/api/v1/me -H "Authorization: Bearer <PASTE_TOKEN>"
  ```
- **Change the password** in any real environment via `FLOWFORGE_ADMIN_PASSWORD`.
- **Roles:** ADMIN (all), MANAGER (author workflows), OPERATOR (trigger executions),
  VIEWER (read-only).
- **Required:** now (to use the secured API locally).

---

## Phase 10 — Docker Desktop (for Testcontainers)

- **What:** install **Docker Desktop for Windows**.
- **Why:** the container-based integration test (`ReportingContainerIT`) starts a real
  throwaway PostgreSQL in Docker, so the suite can run with zero local-DB setup. Without
  Docker the test simply **skips** (the build still passes).
- **Where:** https://www.docker.com/products/docker-desktop/
- **Steps:** download, install, launch Docker Desktop, wait until it says "Engine running".
  On first install it may enable WSL2 — accept the prompts.
- **Verify:**
  ```bash
  docker run --rm hello-world
  ```
  Then the container test will execute (not skip):
  ```bash
  mvn "-Dtest=ReportingContainerIT" "-DfailIfNoTests=false" test
  ```
- **Required:** optional now (tests skip without it); recommended before Phase 12 (Compose).

---

_Docker (above) is also used by Phase 12 (Docker Compose)._

---

## Phase 12 — Run the whole stack with Docker Compose

With Docker Desktop running, you can start **app + database together** with one command —
no local Postgres or env setup needed.

- **What:** `docker compose up --build` builds the app image and starts PostgreSQL + app.
- **Why:** one-command, reproducible run of the entire system (great for a demo/interview).
- **Steps:**
  ```bash
  docker compose up --build
  ```
  Wait for the app health to go green, then:
  ```bash
  curl -s -XPOST localhost:8080/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin12345\"}"
  ```
- **Optional strong secrets** (recommended): before running, set in your shell:
  ```bash
  export FLOWFORGE_JWT_SECRET=$(openssl rand -base64 48)
  export DB_PASSWORD=$(openssl rand -base64 24)
  export FLOWFORGE_ADMIN_PASSWORD=$(openssl rand -base64 18)
  ```
- **Verify:** `curl -s localhost:8080/actuator/health` returns `{"status":"UP"}`.
- **Stop / wipe:** `docker compose down` (add `-v` to also delete the DB volume).
- **Required:** optional — an alternative to the local JDK+Postgres workflow.
