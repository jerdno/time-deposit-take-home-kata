package org.ikigaidigital.application.port.out;

import java.util.List;
import org.ikigaidigital.application.model.VersionedTimeDeposit;

/** Loads every Time Deposit, ordered by id, for a Balance Update. */
public interface LoadAllTimeDepositsPort {

    /** Each Time Deposit comes with the {@code version} it was read at, for {@link SaveBalancesPort}'s check. */
    List<VersionedTimeDeposit> loadAllTimeDeposits();
}
