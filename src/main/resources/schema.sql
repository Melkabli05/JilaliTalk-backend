-- Platform-auth schema (see com.jilali.auth). Idempotent: safe to run on every startup.

CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Real HelloTalk JWTs obtained out-of-band (a real device/app login) — there is no self-service
-- way to mint new ones yet, since the upstream login endpoint uses an undeciphered encrypted
-- binary protocol (bin/cc2018). Each row is "assigned" to at most one app_user; a JilaliTalk
-- account with no assigned row falls back to jilali.default-auth-token, same as before this
-- table existed.
CREATE TABLE IF NOT EXISTS hellotalk_token_pool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hellotalk_uid BIGINT NOT NULL,
    jwt VARCHAR(4000) NOT NULL,
    label VARCHAR(255),
    assigned_to_user_id BIGINT REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Opaque session id, never a JWT — the browser only ever holds this (as an HttpOnly cookie),
-- never the real HelloTalk credential, which stays resolved server-side per request.
CREATE TABLE IF NOT EXISTS app_session (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);
