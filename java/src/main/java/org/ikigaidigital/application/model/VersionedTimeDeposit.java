package org.ikigaidigital.application.model;

import org.ikigaidigital.TimeDeposit;

/**
 * A Time Deposit together with the {@code version} and the Balance it was loaded at. It travels from the load to the
 * save of a Balance Update, so the save can tell whether another transaction changed the Time Deposit in between, and
 * the Balance Update can tell whether the calculator changed its Balance. The domain {@code TimeDeposit} has no version
 * and must not change, so both ride alongside it.
 */
public record VersionedTimeDeposit(TimeDeposit timeDeposit, long version, double loadedBalance) {

    public VersionedTimeDeposit(TimeDeposit timeDeposit, long version) {
        this(timeDeposit, version, timeDeposit.getBalance());
    }

    /** Whether the Time Deposit's Balance differs from the one it was loaded with. */
    public boolean balanceChanged() {
        return timeDeposit.getBalance() != loadedBalance;
    }
}
