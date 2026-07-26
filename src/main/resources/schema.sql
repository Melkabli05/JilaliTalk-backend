-- Idempotent schema for jilalibff. Runs on every startup via AuthSchemaInitializer.
-- All CREATE TABLE statements use IF NOT EXISTS so re-runs are no-ops.
--
-- Conditional ALTER TABLE statements (the auth_session.refreshed_at column) are
-- NOT in this file because SQLite has no native ADD COLUMN IF NOT EXISTS. Those
-- are handled in Java via SchemaGuard + SqliteConnectionInit.

-- ============================================================================
-- auth_session
-- ============================================================================
-- A session IS a verified HelloTalk identity: created only after a real upstream
-- pre_login+login (or signup+login) round-trip succeeds. The browser holds only the
-- opaque `id` (as an HttpOnly cookie) — the `jwt` never leaves this table.
--
-- refreshed_at is added at runtime by AuthSchemaInitializer when it doesn't already
-- exist (see SchemaGuard). It's nullable for legacy rows that predate the JWT-refresh
-- feature, and tracks when the stored jwt was last updated so cleanup jobs can detect
-- stale sessions that were never re-logged-in.
CREATE TABLE IF NOT EXISTS auth_session (
    id VARCHAR(64) PRIMARY KEY,
    hellotalk_uid BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    jwt VARCHAR(4000) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);
-- refreshed_at column is appended via AuthSchemaInitializer (see SchemaGuard usage).

-- ============================================================================
-- ghost_publisher
-- ============================================================================
-- Persists which users are currently "ghost publishing" (speaking from the audience
-- while invisible in the roster) per room. Replaces the in-memory ConcurrentHashMap
-- from GhostPublisherRegistry so ghost publishers survive BFF restarts and are visible
-- to all instances behind a load balancer.
--
-- A late-joining client (one that subscribes to /ws/ht/{cname} AFTER a ghost publisher
-- started) won't see them until the next start/stop toggle re-emits the synthetic
-- stage_join/stage_quit. The last_seen column lets the cleanup job purge abandoned
-- entries (e.g. ghost publisher who closed their tab without calling /stop).
CREATE TABLE IF NOT EXISTS ghost_publisher (
    cname       VARCHAR(255) NOT NULL,
    user_id     BIGINT       NOT NULL,
    started_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (cname, user_id)
);
CREATE INDEX IF NOT EXISTS idx_ghost_publisher_last_seen
    ON ghost_publisher (last_seen);

-- ============================================================================
-- service_token
-- ============================================================================
-- Persists the BFF's service-account HelloTalk JWT across restarts. Seeded from
-- JILALI_DEFAULT_AUTH_TOKEN on first init; refreshed in place by ImReloginRunner when
-- status 105 fires and the relogin succeeds. A single-row table (PRIMARY KEY on a
-- constant 'default' name) — the BFF only ever has one service-account token at a time.
CREATE TABLE IF NOT EXISTS service_token (
    name         VARCHAR(64) PRIMARY KEY,
    jwt          TEXT        NOT NULL,
    refreshed_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Default row inserted on first init via AuthTokenHolder (separate from the schema
-- initializer — the row needs the JWT, which is config-driven, not DDL-driven).

-- ============================================================================
-- profile_cache
-- ============================================================================
-- SQLite-backed replacement for the in-process Caffeine `user-info` cache. Stores the
-- full HelloTalk UserInfo blob as JSON so multi-instance BFFs share a single cache
-- (two instances behind a load balancer used to both cold-call upstream for the same
-- uid). 24h expire-after-access via the scheduled cleanup job, identical to the
-- Caffeine config it replaces.
--
-- We use SQLite's built-in JSON1 (sql >= 3.38, bundled with micronaut-jdbc-sqlite) so
-- future enhancements can index into the JSON blob without re-extracting on every read.
CREATE TABLE IF NOT EXISTS profile_cache (
    user_id       BIGINT PRIMARY KEY,
    payload_json  TEXT        NOT NULL,
    cached_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_profile_cache_last_used ON profile_cache (last_used_at);

-- ============================================================================
-- scheduler_lock
-- ============================================================================
-- Multi-instance leader election for scheduled cleanup jobs. Each BFF instance tries
-- INSERT OR IGNORE on every (name) row at boot + every 30s. The instance that won
-- the row holds the lock and runs the cleanups; others stay idle. Reset by deleting
-- the row, which the next interval's INSERT OR IGNORE re-acquires.
--
-- Single-row per job name. Concurrent INSERT OR IGNORE is safe — only one INSERT
-- succeeds, others are no-ops.
CREATE TABLE IF NOT EXISTS scheduler_lock (
    name         VARCHAR(64) PRIMARY KEY,
    instance_id  VARCHAR(64) NOT NULL,
    acquired_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
