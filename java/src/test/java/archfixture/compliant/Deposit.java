package archfixture.compliant;

import archfixture.compliant.plantype.Plan;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A top-level domain class using only the JDK, the SLF4J API and the domain. */
public class Deposit {

    private static final Logger LOG = LoggerFactory.getLogger(Deposit.class);

    public BigDecimal interest(Plan plan) {
        LOG.info("Plan Type {}", plan);
        return BigDecimal.ZERO;
    }
}
