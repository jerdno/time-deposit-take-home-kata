package org.ikigaidigital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Assumption (ticket 8): this class stays in the domain package because moving it would change Spring's component
// scanning. It is the composition root, the one class there allowed to depend on Spring; the ArchUnit domain rule
// (HexagonalArchitecture) leaves exactly this class out, and no domain class may depend on it.
@SpringBootApplication
public class TimeDepositApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimeDepositApplication.class, args);
    }
}
