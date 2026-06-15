package com.jilali.client;

import com.jilali.core.JilaliEnvelope;
import com.jilali.core.JilaliException;
import io.micronaut.http.HttpStatus;

/**
 * Single point that turns a Jilali envelope into either its payload or a typed exception.
 * <p>
 * I generally avoid utility classes, but this is the one legitimate case: a pure, stateless
 * transform with no dependencies and no state to inject. Making it a {@code @Singleton} bean
 * would be ceremony for nothing. It is package-private to the client layer so it cannot sprawl
 * into a dumping ground.
 */
public final class JilaliResponses {

    private JilaliResponses() {
    }

    /**
     * Unwraps an envelope. Returns {@code null} if upstream sent code=0 with null data —
     * callers that need a non-null payload must use {@link #requireData} instead.
     */
    public static <T> T unwrap(JilaliEnvelope<T> envelope) {
        if (envelope == null) {
            throw new JilaliException(-1, "Empty upstream response", HttpStatus.BAD_GATEWAY);
        }
        if (!envelope.isSuccess()) {
            throw JilaliException.fromCode(envelope.code(), envelope.msg());
        }
        return envelope.data();
    }

    /**
     * Like {@link #unwrap} but throws if upstream returns null data, for endpoints where
     * a null payload is never a valid success response.
     */
    public static <T> T requireData(JilaliEnvelope<T> envelope) {
        T data = unwrap(envelope);
        if (data == null) {
            throw new JilaliException(-1, "Upstream returned null data for a non-nullable endpoint",
                HttpStatus.BAD_GATEWAY);
        }
        return data;
    }
}
