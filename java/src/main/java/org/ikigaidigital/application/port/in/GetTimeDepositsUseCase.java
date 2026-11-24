package org.ikigaidigital.application.port.in;

import org.ikigaidigital.application.model.PageQuery;
import org.ikigaidigital.application.model.TimeDepositPage;

/** Lists Time Deposits with their Withdrawals, a page at a time, ordered by id. */
public interface GetTimeDepositsUseCase {

    TimeDepositPage getTimeDeposits(PageQuery query);
}
