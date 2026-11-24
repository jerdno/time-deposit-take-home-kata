package org.ikigaidigital.plantype;

/** Premium Plan: a fixed annual rate with an Eligibility Window that opens after the first 45 days and never closes. */
public final class PremiumPlan extends FixedRatePlanType {

    private static final String PLAN_TYPE = "premium";
    private static final double ANNUAL_RATE = 0.05;
    private static final int FIRST_ELIGIBLE_DAY = 46;

    public PremiumPlan() {
        super(PLAN_TYPE, ANNUAL_RATE, FIRST_ELIGIBLE_DAY);
    }
}
