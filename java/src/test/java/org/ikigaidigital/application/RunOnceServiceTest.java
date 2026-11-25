package org.ikigaidigital.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.ikigaidigital.application.port.in.RunOnceUseCase;
import org.ikigaidigital.application.port.in.RunOnceUseCase.Outcome;
import org.ikigaidigital.support.DatabaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** The run-once mechanism on its own, with a piece of work that records what it does. */
class RunOnceServiceTest extends DatabaseIntegrationTest {

    private static final String KEY = "run-once-key";

    @Autowired
    RunOnceUseCase runOnce;

    @Autowired
    WriteTransaction writeTransaction;

    private final List<String> steps = new ArrayList<>();

    @Test
    void runsTheWorkWithNoTransactionOpenAndStoresTheKeyInItsWriteTransaction() {
        assertThat(runOnce.runOnce(KEY, recordingWork())).isEqualTo(Outcome.RAN);

        assertThat(steps).containsExactly("compute outside a transaction", "write in a transaction");
        assertThat(storedKeys()).containsExactly(KEY);
    }

    @Test
    void aStoredKeySkipsTheWorkWithoutRunningIt() {
        runOnce.runOnce(KEY, recordingWork());
        steps.clear();

        assertThat(runOnce.runOnce(KEY, recordingWork())).isEqualTo(Outcome.SKIPPED);

        assertThat(steps).isEmpty();
    }

    @Test
    void aKeyStoredWhileTheWorkComputesSkipsItsWrites() {
        Runnable work = () -> {
            // As if a concurrent call with the same key committed while this one was computing.
            jdbc.sql("INSERT INTO idempotency_keys (key) VALUES (?)").param(KEY).update();
            writeTransaction.run(() -> steps.add("write"));
            steps.add("after the write transaction");
        };

        assertThat(runOnce.runOnce(KEY, work)).isEqualTo(Outcome.SKIPPED);

        assertThat(steps).isEmpty();
    }

    @Test
    void aFailedWriteStoresNoKeySoTheNextCallRunsAgain() {
        Runnable failing = () -> writeTransaction.run(() -> {
            throw new IllegalStateException("Simulated failure");
        });

        assertThatThrownBy(() -> runOnce.runOnce(KEY, failing)).hasMessage("Simulated failure");
        assertThat(storedKeys()).isEmpty();

        assertThat(runOnce.runOnce(KEY, recordingWork())).isEqualTo(Outcome.RAN);
        assertThat(storedKeys()).containsExactly(KEY);
    }

    @Test
    void workThatNeverOpensAWriteTransactionIsAnErrorAndStoresNoKey() {
        assertThatThrownBy(() -> runOnce.runOnce(KEY, () -> steps.add("compute")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("finished without a WriteTransaction");

        assertThat(steps).containsExactly("compute");
        assertThat(storedKeys()).isEmpty();
    }

    private Runnable recordingWork() {
        return () -> {
            steps.add("compute " + transactionState());
            writeTransaction.run(() -> steps.add("write " + transactionState()));
        };
    }

    private static String transactionState() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                ? "in a transaction"
                : "outside a transaction";
    }

    private List<String> storedKeys() {
        return jdbc.sql("SELECT key FROM idempotency_keys").query(String.class).list();
    }
}
