package org.ikigaidigital.application;

import org.ikigaidigital.application.port.in.RunOnceUseCase;
import org.ikigaidigital.application.port.out.ClaimIdempotencyKeyPort;
import org.springframework.stereotype.Service;

@Service
class RunOnceService implements RunOnceUseCase {

    private final ClaimIdempotencyKeyPort claimIdempotencyKeyPort;

    RunOnceService(ClaimIdempotencyKeyPort claimIdempotencyKeyPort) {
        this.claimIdempotencyKeyPort = claimIdempotencyKeyPort;
    }

    // No transaction of ours: the work loads and computes with none open, and WriteTransaction claims the key in the
    // work's one write transaction, so the key is committed together with the work's writes, or rolled back with them
    // (docs/adr/0002-idempotency-key-for-the-balance-update.md).
    // Assumption (ticket 11): a concurrent call with the same key waits only while the first call's write transaction
    // is open, and is then a replay (204), not a 409 "still in progress" as the IETF Idempotency-Key draft suggests.
    @Override
    public Outcome runOnce(String idempotencyKey, Runnable work) {
        if (claimIdempotencyKeyPort.isClaimed(idempotencyKey)) {
            return Outcome.SKIPPED;
        }
        try {
            IdempotencyKeyScope.runWith(idempotencyKey, work);
            return Outcome.RAN;
        } catch (IdempotencyKeyAlreadyClaimedException e) {
            return Outcome.SKIPPED;
        }
    }
}
