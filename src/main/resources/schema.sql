-- Auth schema (see com.jilali.auth). Idempotent: safe to run on every startup.

-- A session IS a verified HelloTalk identity: created only after a real upstream
-- pre_login+login (or signup+login) round-trip succeeds. The browser holds only the
-- opaque `id` (as an HttpOnly cookie) — the `jwt` never leaves this table.
CREATE TABLE IF NOT EXISTS auth_session (
    id VARCHAR(64) PRIMARY KEY,
    hellotalk_uid BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    jwt VARCHAR(4000) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);
