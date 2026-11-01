package com.foliotrack.repository;

import com.foliotrack.config.ReadOnlyDataSourceConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

// Runs already-validated SQL from the ask feature on the read-only connection pool, scoped to one user.
@Repository
public class ReadOnlyQueryExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public ReadOnlyQueryExecutor(@Qualifier(ReadOnlyDataSourceConfig.READ_ONLY) DataSource readOnlyDataSource) {
        this.jdbcTemplate = new JdbcTemplate(readOnlyDataSource);
        // Its own transaction manager, created here rather than as a bean, so it can't replace the JPA one that
        // @Transactional uses everywhere else. readOnly makes the driver start the transaction READ ONLY.
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(readOnlyDataSource));
        this.transactionTemplate.setReadOnly(true);
    }

    /**
     * Runs the query with the v_my_* views scoped to userId. The setting is SET LOCAL, so it ends with the
     * transaction and can never leak to the next request that borrows this pooled connection.
     */
    public List<Map<String, Object>> run(long userId, String sql) {
        return transactionTemplate.execute(status -> {
            // SET can't take bind parameters. userId is a long, so the text is only ever digits: no injection.
            jdbcTemplate.execute("SET LOCAL app.user_id = '" + userId + "'");
            // Also set as the role's default in V3; repeated here so the limit is visible where queries run
            jdbcTemplate.execute("SET LOCAL statement_timeout = '5s'");
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            // Nothing should have changed, but never commit LLM-generated SQL
            status.setRollbackOnly();
            return rows;
        });
    }
}
