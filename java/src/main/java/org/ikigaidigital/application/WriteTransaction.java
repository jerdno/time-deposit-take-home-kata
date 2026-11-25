package org.ikigaidigital.application;

import java.util.Optional;
import org.ikigaidigital.application.port.out.ClaimIdempotencyKeyPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one short transaction a use case writes in. A use case loads and computes first, with no transaction of its own
 * open, then hands all its writes to {@link #run(Runnable)}.
 *
 * <p>When the call is guarded by an idempotency key ({@link RunOnceService}), the key is claimed first in the same
 * transaction, so it is stored together with the writes or rolled back with them
 * (docs/adr/0002-idempotency-key-for-the-balance-update.md).
 */
@Component
class WriteTransaction {

    private final ClaimIdempotencyKeyPort claimIdempotencyKeyPort;
    private final TransactionTemplate transactionTemplate;

    WriteTransaction(ClaimIdempotencyKeyPort claimIdempotencyKeyPort, PlatformTransactionManager transactionManager) {
        this.claimIdempotencyKeyPort = claimIdempotencyKeyPort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Runs the writes in one transaction: all of them commit, or none. It joins the caller's transaction when there is
     * one (TransactionTemplate's default PROPAGATION_REQUIRED).
     *
     * @throws IllegalStateException if this call is guarded by an idempotency key and already ran a write transaction
     *     under it
     */
    void run(Runnable writes) {
        Optional<String> idempotencyKey = IdempotencyKeyScope.startWriteTransaction();
        transactionTemplate.executeWithoutResult(status -> {
            // The key is claimed before any write, so a concurrent call with the same key waits here, on the key,
            // and never races these writes.
            if (idempotencyKey.isPresent() && !claimIdempotencyKeyPort.claim(idempotencyKey.get())) {
                throw new IdempotencyKeyAlreadyClaimedException(idempotencyKey.get());
            }
            writes.run();
        });
    }
}
