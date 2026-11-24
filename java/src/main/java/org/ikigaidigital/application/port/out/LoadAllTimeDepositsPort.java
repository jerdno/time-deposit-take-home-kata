package org.ikigaidigital.application.port.out;

import java.util.List;
import org.ikigaidigital.application.model.VersionedTimeDeposit;

/**
 * Loads every Time Deposit, ordered by id, for a Balance Update.
 *
 * <p>Assumption (ticket 7): the whole set of Time Deposits fits in memory. Batching is out of scope.
 */
public interface LoadAllTimeDepositsPort {

    /** Each Time Deposit comes with the {@code version} it was read at, for {@link SaveBalancesPort}'s check. */
    List<VersionedTimeDeposit> loadAllTimeDeposits();
}
