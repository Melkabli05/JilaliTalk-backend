package com.jilali.client;

import com.jilali.core.JilaliEnvelope;
import com.jilali.core.JilaliException;

/**
 * Single point that turns a Jilali envelope into either its payload or a typed exception.
 * <p>
 * I generally avoid utility classes, but this is the one legitimate case: a pure, stateless
 * transform with no dependencies and no state to inject. Making it a {@code @Singleton} bean
 * would be ceremony for nothing. It is package-private to the client layer so it cannot sprawl
 * into a dumping ground.
 */
final class JilaliResponses {

    private JilaliResponses() {
    }

    static <T> T unwrap(JilaliEnvelope<T> envelope) {
        if (envelope == null) {
            throw new JilaliException(-1, "Empty upstream response", io.micronaut.http.HttpStatus.BAD_GATEWAY);
        }
        if (!envelope.isSuccess()) {
            throw JilaliException.fromCode(envelope.code(), envelope.msg());
        }
        return envelope.data();
    }
}
