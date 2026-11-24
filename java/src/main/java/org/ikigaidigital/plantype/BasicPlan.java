package org.ikigaidigital.plantype;

/** Basic Plan: 1% a year, Eligibility Window from day 31 onward. */
public final class BasicPlan extends FixedRatePlanType {

    public BasicPlan() {
        super("basic", 0.01, 31);
    }
}
