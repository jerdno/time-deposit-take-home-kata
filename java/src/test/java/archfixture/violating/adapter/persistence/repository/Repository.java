package archfixture.violating.adapter.persistence.repository;

import archfixture.violating.adapter.web.Controller;

/** A persistence adapter class that depends on the web adapter. */
public class Repository {

    public Controller controller() {
        return new Controller();
    }
}
