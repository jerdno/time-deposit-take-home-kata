package org.ikigaidigital.application;

import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.ikigaidigital.application.port.out.LoadTimeDepositPagePort;
import org.ikigaidigital.application.readmodel.PageQuery;
import org.ikigaidigital.application.readmodel.TimeDepositPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetTimeDepositsService implements GetTimeDepositsUseCase {

    private final LoadTimeDepositPagePort loadTimeDepositPagePort;

    GetTimeDepositsService(LoadTimeDepositPagePort loadTimeDepositPagePort) {
        this.loadTimeDepositPagePort = loadTimeDepositPagePort;
    }

    // One read-only transaction, so the count, the page and its Withdrawals are read on one connection.
    @Override
    @Transactional(readOnly = true)
    public TimeDepositPage getTimeDeposits(PageQuery query) {
        return loadTimeDepositPagePort.loadTimeDepositPage(query);
    }
}
