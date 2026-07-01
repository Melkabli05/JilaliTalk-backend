package com.jilali.core.ws;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Serializes async sends over a single WebSocket so concurrent callers never interleave
 * partial writes. WebSocket.sendText/sendBinary each return CompletableFuture[WebSocket];
 * chaining with handle+thenCompose ensures each send waits for the previous to finish.
 */
public final class SequentialSender {

    private volatile CompletableFuture<WebSocket> chain = CompletableFuture.completedFuture(null);

    /** Queue a send; runs only after every previously queued send has completed. */
    public synchronized void enqueue(Supplier<CompletableFuture<WebSocket>> sendOp, Consumer<Throwable> onError) {
        chain = chain
            .handle((r, t) -> null)
            .thenCompose(r -> sendOp.get())
            .exceptionally(e -> { onError.accept(e); return null; });
    }

    /** Reset the chain after a reconnect where in-flight sends are now moot. */
    public void reset() {
        chain = CompletableFuture.completedFuture(null);
    }
}
