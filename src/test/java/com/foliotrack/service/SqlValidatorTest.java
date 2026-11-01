package com.foliotrack.service;

import com.foliotrack.exception.QueryRejectedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The validator is the guard between LLM output and the database, so every rule gets an attack that should hit it.
// It's a plain class with no dependencies, so the tests just call it: no Spring, no mocks.
class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    // ---------- Allowed queries ----------

    @Test
    void wrapsAValidQueryWithARowLimit() {
        String wrapped = validator.validateAndWrap("SELECT symbol, quantity FROM v_my_holdings");

        assertThat(wrapped).isEqualTo("SELECT * FROM (SELECT symbol, quantity FROM v_my_holdings) q LIMIT 100");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM v_my_account",
            "select symbol from v_my_holdings order by market_value desc",
            "  SELECT SUM(unrealized_pnl) AS total_pnl FROM v_my_holdings  ",
            "SELECT\n  side,\n  COUNT(*)\nFROM v_my_orders\nGROUP BY side",
            "SELECT symbol, SUM(amount) FROM v_my_transactions WHERE type = 'BUY' GROUP BY symbol",
            "SELECT o.symbol, h.quantity FROM v_my_orders o JOIN v_my_holdings h ON h.symbol = o.symbol",
            "SELECT * FROM v_my_orders WHERE status = 'REJECTED' AND created_at > now() - interval '7 days'",
            "SELECT symbol FROM v_my_holdings WHERE quantity > (SELECT AVG(quantity) FROM v_my_holdings)",
            "SELECT symbol FROM v_my_holdings UNION SELECT symbol FROM v_my_orders",
            "SELECT * FROM V_MY_HOLDINGS",
    })
    void acceptsReadOnlyQueriesOnAllowlistedViews(String sql) {
        assertThat(validator.validateAndWrap(sql)).startsWith("SELECT * FROM (").endsWith(") q LIMIT 100");
    }

    // Keywords are matched as whole words: created_at contains "create", order_id contains "order" etc.
    @Test
    void doesNotRejectColumnNamesThatContainAForbiddenKeyword() {
        assertThat(validator.validateAndWrap("SELECT order_id, created_at FROM v_my_orders"))
                .contains("created_at");
    }

    @Test
    void trimsSurroundingWhitespaceBeforeWrapping() {
        assertThat(validator.validateAndWrap("\n  SELECT * FROM v_my_account \n"))
                .isEqualTo("SELECT * FROM (SELECT * FROM v_my_account) q LIMIT 100");
    }

    // ---------- Shape: must be one SELECT ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void rejectsMissingSql(String sql) {
        assertRejected(sql);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "EXPLAIN SELECT * FROM v_my_holdings",
            // A writable CTE: starts with WITH, and its DELETE runs even though the outer statement is a SELECT
            "WITH gone AS (DELETE FROM holdings RETURNING *) SELECT * FROM gone",
            "SHOW ALL",
            "VALUES (1)",
            "TABLE users",
    })
    void rejectsStatementsThatDoNotStartWithSelect(String sql) {
        assertRejected(sql, "single SELECT");
    }

    @Test
    void rejectsSqlThatCannotBeParsed() {
        assertRejected("SELECT FROM WHERE v_my_holdings", "could not be parsed");
    }

    // ---------- Stacked queries and comments ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM v_my_holdings; DROP TABLE users",
            "SELECT * FROM v_my_holdings;",
            "SELECT 1; SELECT * FROM users",
    })
    void rejectsSemicolonsSoQueriesCannotBeStacked(String sql) {
        assertRejected(sql, "Semicolons");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM v_my_holdings -- WHERE quantity > 0",
            "SELECT * FROM v_my_holdings /* hidden */",
            // A comment used to hide the rest of a line so a filter disappears
            "SELECT * FROM v_my_orders WHERE status = 'FILLED' --' AND side = 'BUY'",
            "SELECT * FROM/**/users",
    })
    void rejectsComments(String sql) {
        assertRejected(sql, "comments");
    }

    // ---------- Forbidden keywords (writes, DDL, privileges) ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM v_my_holdings WHERE 1 = 1 OR DROP TABLE users",
            "SELECT * FROM v_my_holdings WHERE symbol IN (INSERT INTO users VALUES (1))",
            "SELECT * FROM v_my_holdings WHERE (UPDATE users SET cash_balance = 0) IS NULL",
            "SELECT delete FROM v_my_holdings",
            "SELECT * FROM v_my_holdings WHERE 1 = (ALTER ROLE foliotrack_readonly SUPERUSER)",
            "SELECT * FROM v_my_holdings WHERE GRANT ALL ON users TO PUBLIC",
            "SELECT * FROM v_my_holdings WHERE COPY users TO '/tmp/x'",
            "SELECT * FROM v_my_holdings WHERE TRUNCATE users",
            "SELECT * FROM v_my_holdings WHERE CREATE TABLE x (id int)",
            "select * from v_my_holdings where 1 = 1 or drop table users",
            // SELECT ... INTO creates a new table from the result
            "SELECT * INTO stolen FROM v_my_holdings",
            // Row locks are a write-side operation
            "SELECT * FROM v_my_holdings FOR UPDATE",
    })
    void rejectsForbiddenKeywords(String sql) {
        assertRejected(sql, "Keyword not allowed");
    }

    // ---------- Table allowlist ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM users",
            "SELECT * FROM holdings",
            "SELECT * FROM orders",
            "SELECT * FROM transactions",
            "SELECT * FROM instruments",
            // UNION into a base table: the classic way to append another user's rows to an allowed result
            "SELECT symbol, quantity FROM v_my_holdings UNION SELECT email, 0 FROM users",
            "SELECT symbol FROM v_my_holdings UNION ALL SELECT CAST(cash_balance AS text) FROM users WHERE id = 2",
            "SELECT * FROM v_my_holdings WHERE quantity > (SELECT COUNT(*) FROM holdings)",
            "SELECT * FROM v_my_holdings h JOIN holdings x ON x.quantity = h.quantity",
            // Comma join: a second table that never follows FROM or JOIN directly
            "SELECT * FROM v_my_holdings, users",
            "SELECT * FROM (SELECT * FROM users) u",
            "SELECT * FROM public.users",
            // Schema-qualified allowlisted name: only the bare view names are allowed
            "SELECT * FROM public.v_my_holdings",
            "SELECT * FROM \"users\"",
    })
    void rejectsTablesOutsideTheAllowlist(String sql) {
        assertRejected(sql, "may only read from");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM pg_catalog.pg_roles",
            "SELECT * FROM pg_user",
            "SELECT * FROM information_schema.tables",
            "SELECT symbol FROM v_my_holdings UNION SELECT usename FROM pg_shadow",
    })
    void rejectsSystemCatalogs(String sql) {
        assertRejected(sql);
    }

    // ---------- Dangerous functions ----------

    @ParameterizedTest
    @ValueSource(strings = {
            // Switches app.user_id mid-query, i.e. tries to read another user's rows through the views
            "SELECT set_config('app.user_id', '2', true), * FROM v_my_holdings",
            "SELECT * FROM v_my_holdings WHERE SET_CONFIG('app.user_id', '2', false) IS NOT NULL",
            "SELECT pg_catalog.set_config('app.user_id', '2', true) FROM v_my_account",
            // Denial of service: the statement timeout would also stop this, but it never needs to run
            "SELECT pg_sleep(60) FROM v_my_account",
            "SELECT pg_read_file('/etc/passwd') FROM v_my_account",
            // query_to_xml runs a SQL string, so the table name hides inside a literal the parser can't see into
            "SELECT query_to_xml('SELECT * FROM users', true, false, '') FROM v_my_account",
            "SELECT table_to_xml('users', true, false, '') FROM v_my_account",
            "SELECT dblink('host=evil', 'SELECT 1') FROM v_my_account",
            "SELECT lo_import('/etc/passwd') FROM v_my_account",
    })
    void rejectsDangerousFunctions(String sql) {
        assertRejected(sql, "Function not allowed");
    }

    // Postgres U&"..." identifiers allow Unicode escapes, which would hide a blocked name from text matching
    @Test
    void rejectsUnicodeEscapedIdentifiers() {
        assertRejected("SELECT U&\"\\0073et_config\"('app.user_id', '2', true) FROM v_my_account",
                "Escaped identifiers");
    }

    private void assertRejected(String sql) {
        assertThatThrownBy(() -> validator.validateAndWrap(sql))
                .isInstanceOf(QueryRejectedException.class)
                .message().isNotBlank();
    }

    // Also checks WHICH rule fired, so an attack can't pass this test by tripping some unrelated rule
    private void assertRejected(String sql, String expectedReason) {
        assertThatThrownBy(() -> validator.validateAndWrap(sql))
                .isInstanceOf(QueryRejectedException.class)
                .hasMessageContaining(expectedReason);
    }
}
