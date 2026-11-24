-- Seed data for the local profile only (docker compose). Every other environment, tests included, gets the schema
-- only. Repeatable rather than versioned, so it never takes a version number that a later schema migration needs.
-- Fixed ids and ON CONFLICT DO NOTHING keep a re-run from duplicating rows or undoing Balance Updates.
--
-- One Time Deposit per Plan Type on each side of its Eligibility Window boundaries
-- (Basic: day 31 onward; Student: day 31 to day 365; Premium: day 46 onward), plus a few Withdrawals.

INSERT INTO time_deposits (id, plan_type, days, balance) VALUES
    (1,  'basic',   30,  10000.00),
    (2,  'basic',   31,  10000.00),
    (3,  'basic',   400, 25000.00),
    (4,  'student', 30,   5000.00),
    (5,  'student', 31,   5000.00),
    (6,  'student', 365,  5000.00),
    (7,  'student', 366,  5000.00),
    (8,  'premium', 31,  50000.00),
    (9,  'premium', 45,  50000.00),
    (10, 'premium', 46,  50000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO withdrawals (id, time_deposit_id, amount, date) VALUES
    (1, 3,  1500.00, '2026-02-10'),
    (2, 3,   250.50, '2026-05-03'),
    (3, 6,   400.00, '2026-03-21'),
    (4, 10, 2000.00, '2026-06-30')
ON CONFLICT (id) DO NOTHING;

-- The rows above have explicit ids: move the identity sequences past them.
SELECT setval(pg_get_serial_sequence('time_deposits', 'id'), (SELECT max(id) FROM time_deposits));
SELECT setval(pg_get_serial_sequence('withdrawals', 'id'), (SELECT max(id) FROM withdrawals));
