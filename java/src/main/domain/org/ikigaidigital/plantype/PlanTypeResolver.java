package org.ikigaidigital.plantype;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ikigaidigital.TimeDeposit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Finds the Plan Type a Time Deposit belongs to among the known Plan Types. */
public final class PlanTypeResolver {

    private static final Logger log = LoggerFactory.getLogger(PlanTypeResolver.class);

    private final Map<String, PlanType> planTypesByValue = new HashMap<>();

    /** @throws IllegalArgumentException if two Plan Types carry the same value */
    public PlanTypeResolver(List<PlanType> planTypes) {
        for (PlanType planType : planTypes) {
            if (planTypesByValue.putIfAbsent(planType.planType(), planType) != null) {
                throw new IllegalArgumentException(
                        "Plan Type '" + planType.planType() + "' is registered more than once");
            }
        }
    }

    /**
     * Returns the known Plan Type whose value equals the Time Deposit's Plan Type exactly (case-sensitive). For any
     * other value it logs an error and returns a Plan Type that pays no interest, so one bad Time Deposit does not
     * stop a Balance Update.
     */
    public PlanType resolve(TimeDeposit timeDeposit) {
        String value = timeDeposit.getPlanType();
        PlanType planType = planTypesByValue.get(value);
        if (planType == null) {
            log.error("Time Deposit {} has unknown Plan Type '{}'; it earns no interest", timeDeposit.getId(), value);
            return new UnknownPlanType(value);
        }
        return planType;
    }
}
