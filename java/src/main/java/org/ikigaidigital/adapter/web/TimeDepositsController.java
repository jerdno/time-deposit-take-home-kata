package org.ikigaidigital.adapter.web;

import java.util.List;
import org.ikigaidigital.adapter.web.api.TimeDepositsApi;
import org.ikigaidigital.adapter.web.api.model.TimeDepositDto;
import org.ikigaidigital.adapter.web.api.model.TimeDepositPageDto;
import org.ikigaidigital.adapter.web.api.model.WithdrawalDto;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.ikigaidigital.application.readmodel.PageQuery;
import org.ikigaidigital.application.readmodel.TimeDepositPage;
import org.ikigaidigital.application.readmodel.TimeDepositView;
import org.ikigaidigital.application.readmodel.WithdrawalView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the API interface generated from {@code openapi.yaml}. */
@RestController
class TimeDepositsController implements TimeDepositsApi {

    private final GetTimeDepositsUseCase getTimeDeposits;

    TimeDepositsController(GetTimeDepositsUseCase getTimeDeposits) {
        this.getTimeDeposits = getTimeDeposits;
    }

    // Assumption (ticket 5): the README asks for "a list"; we return an envelope { items, page, size, totalItems,
    // totalPages } because of pagination. Each item still matches the README schema.
    @Override
    public ResponseEntity<TimeDepositPageDto> getTimeDeposits(Integer page, Integer size) {
        TimeDepositPage result = getTimeDeposits.getTimeDeposits(new PageQuery(page, size));
        return ResponseEntity.ok(new TimeDepositPageDto(
                result.items().stream().map(TimeDepositsController::toDto).toList(),
                result.page(),
                result.size(),
                result.totalItems(),
                result.totalPages()));
    }

    private static TimeDepositDto toDto(TimeDepositView timeDeposit) {
        List<WithdrawalDto> withdrawals = timeDeposit.withdrawals().stream()
                .map(TimeDepositsController::toDto)
                .toList();
        return new TimeDepositDto(
                timeDeposit.id(), timeDeposit.planType(), timeDeposit.balance(), timeDeposit.days(), withdrawals);
    }

    private static WithdrawalDto toDto(WithdrawalView withdrawal) {
        return new WithdrawalDto(withdrawal.id(), withdrawal.amount(), withdrawal.date());
    }
}
