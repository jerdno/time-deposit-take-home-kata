package archfixture.violating.application;

import archfixture.violating.adapter.web.Controller;

/** An application class that depends on an adapter. */
public class Service {

    public Controller controller() {
        return new Controller();
    }
}
