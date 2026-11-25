package org.ikigaidigital.support;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Makes the database reject an UPDATE of a Time Deposit, as if a Balance Update failed partway through. A test installs
 * it with {@link #failWhen} and removes it with {@link #remove} after each test.
 */
public final class FailingTimeDepositUpdates {

    private FailingTimeDepositUpdates() {}

    /**
     * @param condition a PL/pgSQL boolean expression, evaluated for each updated row; {@code OLD} is the row before
     *     the UPDATE (e.g. {@code "OLD.id = 3"})
     */
    public static void failWhen(JdbcClient jdbc, String condition) {
        jdbc.sql("""
                        CREATE FUNCTION fail_balance_update() RETURNS trigger LANGUAGE plpgsql AS $$
                        BEGIN
                            IF %s THEN
                                RAISE EXCEPTION 'Simulated failure updating Time Deposit %%', OLD.id;
                            END IF;
                            RETURN NEW;
                        END
                        $$
                        """.formatted(condition)).update();
        jdbc.sql("CREATE TRIGGER fail_balance_update BEFORE UPDATE ON time_deposits FOR EACH ROW"
                        + " EXECUTE FUNCTION fail_balance_update()")
                .update();
    }

    public static void remove(JdbcClient jdbc) {
        jdbc.sql("DROP TRIGGER IF EXISTS fail_balance_update ON time_deposits").update();
        jdbc.sql("DROP FUNCTION IF EXISTS fail_balance_update()").update();
    }
}
