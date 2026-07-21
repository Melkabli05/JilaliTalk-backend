package com.jilali.roomcontext.domain.valueobject;

/**
 * A HelloTalk voice/live-room channel identifier. Wraps the bare {@code String} passed
 * everywhere in the legacy code so a channel id can never be accidentally substituted with
 * an unrelated string (a nickname, a device id, ...) at a call site — the compiler catches it.
 */
public record Cname(String value) {

    public Cname {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("cname must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
