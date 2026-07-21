package com.jilali.roomcontext.infrastructure.client;

import com.jilali.core.JilaliEnvelope;
import com.jilali.core.JilaliException;
import io.micronaut.http.HttpStatus;

/** Dedicated envelope-unwrap helper for this bounded context - a pure, stateless transform with
 *  no dependency on the legacy client.JilaliResponses (which lives in the very "god client"
 *  package this module must not depend on). Same logic, owned here. */
public final class JilaliResponses {

    private JilaliResponses() {}

    public static <T> T unwrap(JilaliEnvelope<T> envelope) {
        if (envelope == null) {
            throw new JilaliException(-1, "Empty upstream response", HttpStatus.BAD_GATEWAY);
        }
        if (!envelope.isSuccess()) {
            throw JilaliException.fromCode(envelope.code(), envelope.msg());
        }
        return envelope.data();
    }

    public static <T> T requireData(JilaliEnvelope<T> envelope) {
        T data = unwrap(envelope);
        if (data == null) {
            throw new JilaliException(-1, "Upstream returned null data for a non-nullable endpoint",
                HttpStatus.BAD_GATEWAY);
        }
        return data;
    }
}
