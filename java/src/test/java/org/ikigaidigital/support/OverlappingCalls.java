package org.ikigaidigital.support;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Makes two calls overlap deterministically. A SHARE lock on {@code time_deposits} lets a Balance Update load every
 * Time Deposit but blocks its UPDATE, so its transaction stays open. Once the expected statements wait on a lock, the
 * lock is released and the calls go on.
 */
public final class OverlappingCalls {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final DataSource dataSource;
    private final JdbcClient jdbc;

    public OverlappingCalls(DataSource dataSource, JdbcClient jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    /**
     * Runs both calls on their own threads while {@code time_deposits} is locked, and returns their results in order.
     *
     * @param waiting for each query pattern ({@code ILIKE}, e.g. {@code "update time_deposits%"}), how many statements
     *     matching it must be waiting on a lock before the lock is released
     */
    public <T> List<T> run(Callable<T> first, Callable<T> second, Map<String, Integer> waiting) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try (Statement statement = lock.createStatement()) {
                statement.execute("LOCK TABLE time_deposits IN SHARE MODE");
            }
            Future<T> firstResult = executor.submit(first);
            Future<T> secondResult = executor.submit(second);
            for (Map.Entry<String, Integer> statement : waiting.entrySet()) {
                awaitWaitingStatements(statement.getKey(), statement.getValue());
            }
            lock.commit();
            return List.of(
                    firstResult.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    secondResult.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitWaitingStatements(String queryPattern, int expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (jdbc.sql("""
                        SELECT count(*) FROM pg_stat_activity
                        WHERE wait_event_type = 'Lock' AND query ILIKE ?
                        """).param(queryPattern).query(Long.class).single() < expected) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError(
                        "Expected " + expected + " statements like '" + queryPattern + "' waiting on a lock");
            }
            Thread.sleep(20);
        }
    }
}
