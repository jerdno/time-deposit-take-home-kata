package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.ikigaidigital.support.OverlappingCalls;
import org.ikigaidigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class ConcurrentBalanceUpdateTest extends PostgresIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void twoSimultaneousBalanceUpdatesGiveOneNoContentAndOneConflictAndApplyOneMonthOfInterest() throws Exception {
        int basic = insertTimeDeposit("basic", "1200.00", 31);
        int premium = insertTimeDeposit("premium", "1200.00", 46);

        // Both Balance Updates load the same versions before either saves: both UPDATEs wait on the table lock.
        List<MvcTestResult> results = new OverlappingCalls(dataSource, jdbc)
                .run(this::postBalanceUpdate, this::postBalanceUpdate, Map.of("update time_deposits%", 2));

        assertThat(results)
                .extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT.value(), HttpStatus.CONFLICT.value());
        MvcTestResult conflict = results.stream()
                .filter(result -> result.getResponse().getStatus() == HttpStatus.CONFLICT.value())
                .findFirst()
                .orElseThrow();
        assertThat(conflict)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "title": "Conflict",
                          "status": 409,
                          "detail": "A concurrent Balance Update changed a Time Deposit first. This one was rolled back and applied no interest."
                        }
                        """);

        // Exactly one month: 1200.00 x 1% / 12 = 1.00 and 1200.00 x 5% / 12 = 5.00.
        assertThat(balanceOf(basic)).isEqualByComparingTo("1201.00");
        assertThat(balanceOf(premium)).isEqualByComparingTo("1205.00");
    }

    private MvcTestResult postBalanceUpdate() {
        return mvc.post().uri("/time-deposits/balance-updates").exchange();
    }
}
