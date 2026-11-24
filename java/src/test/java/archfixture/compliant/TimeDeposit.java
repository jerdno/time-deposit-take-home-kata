package archfixture.compliant;

import archfixture.compliant.plantype.BasicPlan;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A top-level domain class using only the JDK, the SLF4J API and the domain. */
public class TimeDeposit {

    private static final Logger LOG = LoggerFactory.getLogger(TimeDeposit.class);

    public BigDecimal monthlyInterest(BasicPlan basicPlan) {
        LOG.info("Plan Type {}", basicPlan);
        return BigDecimal.ZERO;
    }
}
