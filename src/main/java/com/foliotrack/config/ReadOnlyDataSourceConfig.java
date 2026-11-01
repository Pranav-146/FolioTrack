package com.foliotrack.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// A second connection pool for the ask feature, logged in as foliotrack_readonly (created in migration V3).
//
// defaultCandidate = false keeps this pool out of normal by-type injection, so Spring Boot still auto-configures
// the main DataSource that JPA and Flyway use, and nothing gets the read-only pool unless it asks by name.
@Configuration
public class ReadOnlyDataSourceConfig {

    public static final String READ_ONLY = "readOnlyDataSource";

    // Binds foliotrack.readonly-datasource.* (jdbc-url, username, password, maximum-pool-size) onto Hikari.
    // The bean name doubles as its qualifier, so @Qualifier(READ_ONLY) at the injection point finds it.
    @Bean(name = READ_ONLY, defaultCandidate = false)
    @ConfigurationProperties("foliotrack.readonly-datasource")
    public HikariDataSource readOnlyDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("foliotrack-readonly");
        return dataSource;
    }
}
