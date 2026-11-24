package org.ikigaidigital.application.port.out;

import org.ikigaidigital.application.model.PageQuery;
import org.ikigaidigital.application.model.TimeDepositPage;

/** Loads a page of Time Deposits, ordered by id, each with all of its Withdrawals. */
public interface LoadTimeDepositPagePort {

    TimeDepositPage loadTimeDepositPage(PageQuery query);
}
