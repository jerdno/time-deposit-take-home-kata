package org.ikigaidigital.application.port.out;

import java.util.List;
import org.ikigaidigital.TimeDeposit;

/** Loads every Time Deposit, ordered by id, for a Balance Update. */
public interface LoadAllTimeDepositsPort {

    List<TimeDeposit> loadAllTimeDeposits();
}
