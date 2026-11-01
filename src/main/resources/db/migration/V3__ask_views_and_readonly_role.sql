-- "Ask your portfolio": LLM-generated SQL runs only against these views, as a role that can read nothing else.
--
-- Each view filters by the per-request setting app.user_id, which the app sets with SET LOCAL inside the
-- query's transaction. current_setting(..., true) returns NULL instead of erroring when it is unset, so a
-- missing user id matches no rows (fail closed) rather than all rows.
--
-- security_barrier stops the planner from running caller-supplied conditions (e.g. a failing cast whose error
-- message would echo a value) on rows before the user filter has removed other users' rows.
-- Views run with their owner's privileges, so the read-only role needs SELECT on the views, not the tables.

CREATE VIEW v_my_account WITH (security_barrier) AS
SELECT u.name,
       u.cash_balance
FROM users u
WHERE u.id = NULLIF(current_setting('app.user_id', true), '')::bigint;

-- Values are computed here the same way as the portfolio endpoint, so the LLM doesn't have to get the math right
CREATE VIEW v_my_holdings WITH (security_barrier) AS
SELECT i.symbol,
       i.name                                         AS instrument_name,
       h.quantity,
       h.avg_cost,
       i.current_price,
       i.current_price * h.quantity                   AS market_value,
       h.avg_cost * h.quantity                        AS cost_basis,
       (i.current_price - h.avg_cost) * h.quantity    AS unrealized_pnl
FROM holdings h
JOIN instruments i ON i.id = h.instrument_id
WHERE h.user_id = NULLIF(current_setting('app.user_id', true), '')::bigint;

CREATE VIEW v_my_orders WITH (security_barrier) AS
SELECT o.id AS order_id,
       i.symbol,
       o.side,
       o.quantity,
       o.price_at_execution,
       o.status,
       o.rejection_reason,
       o.created_at
FROM orders o
JOIN instruments i ON i.id = o.instrument_id
WHERE o.user_id = NULLIF(current_setting('app.user_id', true), '')::bigint;

CREATE VIEW v_my_transactions WITH (security_barrier) AS
SELECT t.id AS transaction_id,
       t.order_id,
       i.symbol,
       t.type,
       t.amount,
       t.created_at
FROM transactions t
JOIN orders o ON o.id = t.order_id
JOIN instruments i ON i.id = o.instrument_id
WHERE t.user_id = NULLIF(current_setting('app.user_id', true), '')::bigint;

-- Roles are cluster-wide, so create-or-update keeps this migration working on a fresh database in an
-- existing cluster. The password is a Flyway placeholder filled from DB_READONLY_PASSWORD, never committed.
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'foliotrack_readonly') THEN
        EXECUTE format('ALTER ROLE foliotrack_readonly LOGIN PASSWORD %L', '${readonly_password}');
    ELSE
        EXECUTE format('CREATE ROLE foliotrack_readonly LOGIN PASSWORD %L', '${readonly_password}');
    END IF;
END
$$;

-- Defense in depth on top of the app's read-only transaction: every session of this role is read-only
-- and any query is killed after 5 seconds.
ALTER ROLE foliotrack_readonly SET default_transaction_read_only = on;
ALTER ROLE foliotrack_readonly SET statement_timeout = '5s';

GRANT SELECT ON v_my_account, v_my_holdings, v_my_orders, v_my_transactions TO foliotrack_readonly;

-- set_config() is the only way to change app.user_id from inside a SELECT, i.e. to switch to another user's
-- rows. The app uses the SET LOCAL statement instead, so nobody but superusers (who bypass this) needs it.
REVOKE EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) FROM PUBLIC;
