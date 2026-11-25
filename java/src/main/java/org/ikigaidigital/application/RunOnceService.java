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
