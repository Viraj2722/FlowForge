-- =====================================================================
-- V2: seed immutable reference data.
--
-- Roles are fixed, application-defined values that must exist for security to work.
-- Seeding them in a migration (rather than at app startup) keeps the DB self-contained
-- and reproducible. ON CONFLICT DO NOTHING makes this safe if a row already exists.
--
-- We do NOT seed users here: real accounts are created via the API with a properly
-- hashed password (Phase 9). A bootstrap admin is added in the security phase, with its
-- initial password supplied by an environment variable - never hardcoded in SQL.
-- =====================================================================

INSERT INTO roles (name, description) VALUES
    ('ADMIN',    'Full administrative access to all resources'),
    ('MANAGER',  'Create/modify workflows and act on approvals'),
    ('OPERATOR', 'Trigger and monitor workflow executions'),
    ('VIEWER',   'Read-only access to workflows and executions')
ON CONFLICT (name) DO NOTHING;
