package com.jilali.core;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Mirrors Jilali's universal response envelope: {@code {"code":0,"msg":"ok","data":...}}.
 * <p>
 * This is an <em>internal</em> contract — it is unwrapped by {@link JilaliClient} callers and
 * never leaks to our own frontend, which receives clean HTTP semantics instead.
 *
 * @param <T> shape of the {@code data} payload
 */
@Serdeable
public record JilaliEnvelope<T>(int code, @Nullable String msg, @Nullable T data) {

    /** Jilali signals success with code 0. */
    public boolean isSuccess() {
        return code == 0;
    }
}
