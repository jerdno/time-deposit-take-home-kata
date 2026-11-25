package org.ikigaidigital.plantype;

import java.util.OptionalInt;
import org.ikigaidigital.TimeDeposit;

/**
 * A Plan Type paying a fixed annual rate, divided by twelve, on the Balance during its Eligibility Window: from
 * {@code firstEligibleDay} up to and including {@code lastEligibleDay}, if it has one.
 */
public abstract class FixedRatePlanType implements PlanType {

    private final String planType;
    private final double annualRate;
    private final int firstEligibleDay;
    private final OptionalInt lastEligibleDay;

    protected FixedRatePlanType(String planType, double annualRate, int firstEligibleDay) {
        this(planType, annualRate, firstEligibleDay, OptionalInt.empty());
    }

    protected FixedRatePlanType(String planType, double annualRate, int firstEligibleDay, int lastEligibleDay) {
        this(planType, annualRate, firstEligibleDay, OptionalInt.of(lastEligibleDay));
    }

    private FixedRatePlanType(String planType, double annualRate, int firstEligibleDay, OptionalInt lastEligibleDay) {
        this.planType = planType;
        this.annualRate = annualRate;
        this.firstEligibleDay = firstEligibleDay;
        this.lastEligibleDay = lastEligibleDay;
    }

    @Override
    public final String planType() {
        return planType;
    }

    @Override
    public final boolean isEligible(TimeDeposit timeDeposit) {
        int days = timeDeposit.getDays();
        return days >= firstEligibleDay && (lastEligibleDay.isEmpty() || days <= lastEligibleDay.getAsInt());
    }

    @Override
    public final double calculateMonthlyInterest(TimeDeposit timeDeposit) {
        // Same operation order as the original calculator (Balance × rate, then / 12), so the double result,
        // and therefore the rounded Monthly Interest, is bit-for-bit what it was.
        return timeDeposit.getBalance() * annualRate / 12;
    }
}
