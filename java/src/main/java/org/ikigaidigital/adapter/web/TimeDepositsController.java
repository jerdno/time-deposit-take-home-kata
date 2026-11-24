package org.ikigaidigital.adapter.web;

import java.util.List;
import org.ikigaidigital.adapter.web.api.TimeDepositsApi;
import org.ikigaidigital.adapter.web.api.model.TimeDepositDto;
import org.ikigaidigital.adapter.web.api.model.TimeDepositPageDto;
import org.ikigaidigital.adapter.web.api.model.WithdrawalDto;
import org.ikigaidigital.application.model.PageQuery;
import org.ikigaidigital.application.model.TimeDepositPage;
import org.ikigaidigital.application.model.TimeDepositView;
import org.ikigaidigital.application.model.WithdrawalView;
import org.ikigaidigital.application.port.in.ApplyBalanceUpdateUseCase;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the API interface generated from {@code openapi.yaml}. */
@RestController
class TimeDepositsController implements TimeDepositsApi {

    private final GetTimeDepositsUseCase getTimeDeposits;
    private final ApplyBalanceUpdateUseCase applyBalanceUpdate;
    private final IdempotentRequests idempotentRequests;

    TimeDepositsController(
            GetTimeDepositsUseCase getTimeDeposits,
            ApplyBalanceUpdateUseCase applyBalanceUpdate,
            IdempotentRequests idempotentRequests) {
        this.getTimeDeposits = getTimeDeposits;
        this.applyBalanceUpdate = applyBalanceUpdate;
        this.idempotentRequests = idempotentRequests;
    }

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

    @Override
    public ResponseEntity<Void> applyBalanceUpdate(@Nullable String idempotencyKey) {
        idempotentRequests.run(idempotencyKey, applyBalanceUpdate::applyBalanceUpdate);
        return ResponseEntity.noContent().build();
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
