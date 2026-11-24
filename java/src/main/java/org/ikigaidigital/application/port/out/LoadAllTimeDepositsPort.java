package org.ikigaidigital.application.port.out;

import java.util.List;
import org.ikigaidigital.TimeDeposit;

/**
 * Loads every Time Deposit, ordered by id, for a Balance Update.
 *
 * <p>Assumption (ticket 7): the whole set of Time Deposits fits in memory. Batching is out of scope.
 */
public interface LoadAllTimeDepositsPort {

    List<TimeDeposit> loadAllTimeDeposits();
}
