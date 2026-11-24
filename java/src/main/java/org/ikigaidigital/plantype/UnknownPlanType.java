package org.ikigaidigital.plantype;

import org.ikigaidigital.TimeDeposit;

/**
 * Null object for a Plan Type value no known Plan Type carries (bad data): never eligible, zero Monthly Interest.
 * Only {@link PlanTypeResolver} creates it.
 */
record UnknownPlanType(String planType) implements PlanType {

    @Override
    public boolean isEligible(TimeDeposit timeDeposit) {
        return false;
    }

    @Override
    public double calculateMonthlyInterest(TimeDeposit timeDeposit) {
        return 0;
    }
}
