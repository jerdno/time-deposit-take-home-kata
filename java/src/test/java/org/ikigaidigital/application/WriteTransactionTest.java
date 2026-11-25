package org.ikigaidigital.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.ikigaidigital.application.port.in.RunOnceUseCase;
import org.ikigaidigital.support.DatabaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** The one write transaction of a use case, with and without an idempotency key bound. */
class WriteTransactionTest extends DatabaseIntegrationTest {

    private static final String KEY = "write-transaction-key";

    @Autowired
    WriteTransaction writeTransaction;

    @Autowired
    RunOnceUseCase runOnce;

    private final List<String> seen = new ArrayList<>();

    @Test
    void withoutAKeyTheWritesRunInAPlainTransaction() {
        writeTransaction.run(() -> {
            seen.add("in a transaction: " + TransactionSynchronizationManager.isActualTransactionActive());
            insertTimeDeposit("basic", "1200.00", 31);
        });

        assertThat(seen).containsExactly("in a transaction: true");
        assertThat(timeDepositCount()).isEqualTo(1);
        assertThat(storedKeys()).isEmpty();
    }

    @Test
    void aBoundKeyIsClaimedInTheSameTransactionBeforeTheWrites() {
        runOnce.runOnce(
                KEY,
                () -> writeTransaction.run(() -> {
                    // The key is not committed yet, so only this transaction can see it.
                    seen.addAll(storedKeys());
                    insertTimeDeposit("basic", "1200.00", 31);
                }));

        assertThat(seen).containsExactly(KEY);
        assertThat(storedKeys()).containsExactly(KEY);
        assertThat(timeDepositCount()).isEqualTo(1);
    }

    @Test
    void aFailedWriteRollsTheKeyBackWithIt() {
        assertThatThrownBy(() -> runOnce.runOnce(
                        KEY,
                        () -> writeTransaction.run(() -> {
                            insertTimeDeposit("basic", "1200.00", 31);
                            throw new IllegalStateException("Simulated failure");
                        })))
                .hasMessage("Simulated failure");

        assertThat(storedKeys()).isEmpty();
        assertThat(timeDepositCount()).isZero();
    }

    @Test
    void aSecondWriteTransactionUnderOneKeyIsAnError() {
        assertThatThrownBy(() -> runOnce.runOnce(KEY, () -> {
                    writeTransaction.run(() -> insertTimeDeposit("basic", "1200.00", 31));
                    writeTransaction.run(() -> insertTimeDeposit("basic", "1300.00", 31));
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("second WriteTransaction");

        // The first write transaction committed with the key; the second never opened.
        assertThat(storedKeys()).containsExactly(KEY);
        assertThat(timeDepositCount()).isEqualTo(1);
    }

    private long timeDepositCount() {
        return jdbc.sql("SELECT count(*) FROM time_deposits").query(Long.class).single();
    }

    private List<String> storedKeys() {
        return jdbc.sql("SELECT key FROM idempotency_keys").query(String.class).list();
    }
}
