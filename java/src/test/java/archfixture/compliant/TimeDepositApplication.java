package archfixture.compliant;

import org.springframework.boot.SpringApplication;

/** The composition root: in the domain package, but allowed to depend on Spring. */
public class TimeDepositApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimeDepositApplication.class, args);
    }
}
