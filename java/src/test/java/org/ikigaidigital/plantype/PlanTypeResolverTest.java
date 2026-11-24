package org.ikigaidigital.plantype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.ikigaidigital.TimeDeposit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PlanTypeResolverTest {

    private final BasicPlan basicPlan = new BasicPlan();
    private final StudentPlan studentPlan = new StudentPlan();
    private final PremiumPlan premiumPlan = new PremiumPlan();

    private final PlanTypeResolver resolver = new PlanTypeResolver(List.of(basicPlan, studentPlan, premiumPlan));

    // Captures what the resolver logs, straight from its Logback logger.
    private final Logger resolverLogger = (Logger) LoggerFactory.getLogger(PlanTypeResolver.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logged.start();
        resolverLogger.addAppender(logged);
    }

    @AfterEach
    void stopCapturingLogs() {
        resolverLogger.detachAppender(logged);
    }

    private static TimeDeposit timeDeposit(int id, String planType) {
        return new TimeDeposit(id, planType, 1200.00, 100);
    }

    @Test
    void resolvesEachKnownPlanTypeByItsExactValue() {
        assertThat(resolver.resolve(timeDeposit(1, "basic"))).isSameAs(basicPlan);
        assertThat(resolver.resolve(timeDeposit(2, "student"))).isSameAs(studentPlan);
        assertThat(resolver.resolve(timeDeposit(3, "premium"))).isSameAs(premiumPlan);
    }

    @Test
    void resolvesAnUnknownValueToAPlanTypeThatPaysNoInterest() {
        TimeDeposit gold = timeDeposit(7, "gold");

        PlanType planType = resolver.resolve(gold);

        assertThat(planType.isEligible(gold)).isFalse();
        assertThat(planType.calculateMonthlyInterest(gold)).isZero();
    }

    @Test
    void logsTheTimeDepositIdAndTheUnknownValueAtError() {
        resolver.resolve(timeDeposit(7, "gold"));

        assertThat(logged.list)
                .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        tuple(Level.ERROR, "Time Deposit 7 has unknown Plan Type 'gold'; it earns no interest"));
    }

    @Test
    void matchesCaseSensitively() {
        TimeDeposit capitalisedBasic = timeDeposit(8, "Basic");

        PlanType planType = resolver.resolve(capitalisedBasic);

        assertThat(planType).isNotSameAs(basicPlan);
        assertThat(planType.isEligible(capitalisedBasic)).isFalse();
        assertThat(logged.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.ERROR);
    }

    // Assumption (ticket 4): a missing (null) Plan Type is bad data like any unknown value: logged, no interest.
    @Test
    void treatsAMissingValueAsUnknown() {
        TimeDeposit missing = timeDeposit(9, null);

        PlanType planType = resolver.resolve(missing);

        assertThat(planType.isEligible(missing)).isFalse();
        assertThat(logged.list)
                .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        tuple(Level.ERROR, "Time Deposit 9 has unknown Plan Type 'null'; it earns no interest"));
    }

    @Test
    void logsNothingForAKnownValue() {
        resolver.resolve(timeDeposit(1, "basic"));

        assertThat(logged.list).isEmpty();
    }

    @Test
    void refusesTwoPlanTypesWithTheSameValue() {
        List<PlanType> twoBasicPlans = List.of(new BasicPlan(), studentPlan, new BasicPlan());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PlanTypeResolver(twoBasicPlans))
                .withMessageContaining("'basic'");
    }
}
