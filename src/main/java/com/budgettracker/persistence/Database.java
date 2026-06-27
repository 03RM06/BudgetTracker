package com.budgettracker.persistence;

import com.budgettracker.util.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

public final class Database {

    private static volatile HikariDataSource dataSource;

    private Database() {}

    public static synchronized void init() {
        if (dataSource != null) return;

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(AppConfig.getDbUrl());
        cfg.setUsername(AppConfig.getDbUser());
        cfg.setPassword(AppConfig.getDbPassword());
        cfg.setMaximumPoolSize(5);
        cfg.setConnectionTimeout(30_000);

        dataSource = new HikariDataSource(cfg);

        Flyway.configure()
              .dataSource(dataSource)
              .locations("classpath:db/migration")
              .schemas("budgettracker_db")
              .baselineOnMigrate(true)
              .baselineVersion("0")
              .load()
              .migrate();
    }

    static DataSource dataSource() {
        if (dataSource == null) throw new IllegalStateException("Database.init() has not been called");
        return dataSource;
    }

    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }
}
