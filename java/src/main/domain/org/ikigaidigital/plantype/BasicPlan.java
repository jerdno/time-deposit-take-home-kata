package org.ikigaidigital.plantype;

/** Basic Plan: a fixed annual rate with an Eligibility Window that opens after the first 30 days and never closes. */
public final class BasicPlan extends FixedRatePlanType {

    private static final String PLAN_TYPE = "basic";
    private static final double ANNUAL_RATE = 0.01;
    private static final int FIRST_ELIGIBLE_DAY = 31;

    public BasicPlan() {
        super(PLAN_TYPE, ANNUAL_RATE, FIRST_ELIGIBLE_DAY);
    }
}
