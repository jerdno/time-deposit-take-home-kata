package org.ikigaidigital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import org.ikigaidigital.support.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class DatabaseMigrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void withoutLocalProfileMigrationsCreateTheSchemaAndNoRows() {
        assertThat(count("time_deposits")).isZero();
        assertThat(count("withdrawals")).isZero();
    }

    @Test
    void moneyIsStoredAsNumeric19Scale2() {
        List<Map<String, Object>> moneyColumns = jdbc.sql("""
                        SELECT table_name, column_name, data_type, numeric_precision, numeric_scale
                        FROM information_schema.columns
                        WHERE (table_name, column_name) IN (('time_deposits', 'balance'), ('withdrawals', 'amount'))
                        ORDER BY table_name
                        """).query().listOfRows();

        assertThat(moneyColumns)
                .extracting("table_name", "data_type", "numeric_precision", "numeric_scale")
                .containsExactly(tuple("time_deposits", "numeric", 19, 2), tuple("withdrawals", "numeric", 19, 2));
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
