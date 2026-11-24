package archfixture.compliant.application.port.in;

import archfixture.compliant.TimeDeposit;

/** An in-port, depending on the domain. */
public interface UseCase {

    TimeDeposit timeDeposit();
}
