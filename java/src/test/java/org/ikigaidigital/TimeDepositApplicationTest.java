package org.ikigaidigital;

import org.ikigaidigital.support.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class TimeDepositApplicationTest {

    @Test
    void applicationContextStarts() {}
}
