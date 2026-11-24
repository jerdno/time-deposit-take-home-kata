package archfixture.violating.plantype;

import jakarta.persistence.Entity;
import jakarta.servlet.http.HttpServletRequest;

/** A Plan Type class that depends on Jakarta Persistence and on a web type. */
@Entity
public class BasicPlan {

    public boolean isEligible(HttpServletRequest request) {
        return request != null;
    }
}
