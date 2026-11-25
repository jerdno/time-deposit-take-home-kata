package org.ikigaidigital.adapter.web;

import org.ikigaidigital.application.port.in.RunOnceUseCase;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Makes an endpoint safe to retry with an {@code Idempotency-Key} request header. An endpoint opts in by passing the
 * header and its work, a use case call that writes through the application's {@code WriteTransaction}: without a key
 * the work simply runs, with one it runs at most once per key (docs/adr/0002-idempotency-key-for-the-balance-update.md).
 */
@Component
class IdempotentRequests {

    private final RunOnceUseCase runOnce;

    IdempotentRequests(RunOnceUseCase runOnce) {
        this.runOnce = runOnce;
    }

    /**
     * @param idempotencyKey the header value, already checked against the contract's pattern: a bare key, or one in
     *     double quotes
     */
    void run(@Nullable String idempotencyKey, Runnable work) {
        if (idempotencyKey == null) {
            work.run();
            return;
        }
        String key = unquote(idempotencyKey);
        // The key matches the contract's pattern (letters, digits, ._:+/=-), so it is safe to log.
        runOnce.runOnce(key, work);
    }

    private static String unquote(String idempotencyKey) {
        return idempotencyKey.startsWith("\"")
                ? idempotencyKey.substring(1, idempotencyKey.length() - 1)
                : idempotencyKey;
    }
}
