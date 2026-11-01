package com.foliotrack.service;

import com.foliotrack.exception.QueryRejectedException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Checks LLM-generated SQL before it is run. This is a fast, explainable first line of defense; the real
// security boundary is the database: the query runs as a role that can only SELECT from the user-scoped views.
// Rules are deliberately strict (fail closed): a legitimate query that trips one just gets a "rephrase" error,
// while a malicious one that slipped through would be a data leak.
@Component
public class SqlValidator {

    public static final Set<String> ALLOWED_VIEWS =
            Set.of("v_my_account", "v_my_holdings", "v_my_orders", "v_my_transactions");

    static final int MAX_ROWS = 100;

    private static final Pattern STARTS_WITH_SELECT = Pattern.compile("^SELECT\\b", Pattern.CASE_INSENSITIVE);

    // Whole words only (\b), so column names like created_at or order_id don't match CREATE or ORDER.
    // INTO blocks SELECT ... INTO (creates a table); UPDATE also covers SELECT ... FOR UPDATE row locks.
    private static final Pattern FORBIDDEN_KEYWORD = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|MERGE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|COPY|CALL|DO|EXECUTE"
                    + "|PREPARE|SET|RESET|LOCK|VACUUM|INTO)\\b",
            Pattern.CASE_INSENSITIVE);

    // Functions that change settings (set_config could switch app.user_id to another user), touch the server
    // (pg_sleep, pg_read_file, large objects, dblink) or run a SQL string the parser can't inspect (*_to_xml).
    private static final Pattern FORBIDDEN_FUNCTION = Pattern.compile(
            "\\b(set_config|pg_\\w*|\\w*_to_xml\\w*|dblink\\w*|lo_\\w+)\\b",
            Pattern.CASE_INSENSITIVE);

    // U&"\0073et_config" is set_config written with Unicode escapes, which would hide it from the pattern above
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\bU&['\"]", Pattern.CASE_INSENSITIVE);

    /**
     * Returns the query wrapped in a row limit, ready to run, or throws QueryRejectedException with a safe reason.
     */
    public String validateAndWrap(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new QueryRejectedException("No SQL was generated");
        }
        String query = sql.strip();

        if (!STARTS_WITH_SELECT.matcher(query).find()) {
            throw new QueryRejectedException("Only a single SELECT statement is allowed");
        }
        // No ';' means no second statement can be stacked after the first; no comments means nothing can be hidden
        if (query.contains(";") || query.contains("--") || query.contains("/*")) {
            throw new QueryRejectedException("Semicolons and SQL comments are not allowed");
        }
        rejectIfFound(FORBIDDEN_KEYWORD, query, "Keyword not allowed: ");
        rejectIfFound(FORBIDDEN_FUNCTION, query, "Function not allowed: ");
        rejectIfFound(UNICODE_ESCAPE, query, "Escaped identifiers are not allowed: ");

        // Regex can't reliably find every table a query reads (comma joins, subqueries, quoting), so parse it
        Statement statement = parse(query);
        if (!(statement instanceof Select)) {
            throw new QueryRejectedException("Only a single SELECT statement is allowed");
        }
        // "OrOtherSources" also reports CTE names etc., so anything a query reads from is checked, not just tables
        for (String table : new TablesNamesFinder<Void>().getTablesOrOtherSources(statement)) {
            // Postgres folds unquoted names to lower case, so V_MY_HOLDINGS is the same view. Anything quoted or
            // schema-qualified keeps its quotes/dot here and so never matches: rejected.
            if (!ALLOWED_VIEWS.contains(table.toLowerCase(Locale.ROOT))) {
                throw new QueryRejectedException("Query may only read from " + ALLOWED_VIEWS.stream().sorted().toList()
                        + ", not: " + table);
            }
        }

        // The outer LIMIT caps the result even if the generated query has no limit (or a huge one)
        return "SELECT * FROM (" + query + ") q LIMIT " + MAX_ROWS;
    }

    private static void rejectIfFound(Pattern pattern, String query, String reason) {
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            throw new QueryRejectedException(reason + matcher.group());
        }
    }

    private static Statement parse(String query) {
        try {
            return CCJSqlParserUtil.parse(query);
        } catch (JSQLParserException e) {
            throw new QueryRejectedException("SQL could not be parsed");
        }
    }
}
