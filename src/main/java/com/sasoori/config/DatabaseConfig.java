package com.sasoori.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;

/**
 * Creates and owns the HikariCP connection pool.
 * Call {@link #close()} on context destroy.
 */
public class DatabaseConfig {

    private static final Logger log = LogManager.getLogger(DatabaseConfig.class);

    private final HikariDataSource dataSource;

    public DatabaseConfig(AppConfig cfg) {
        HikariConfig hk = new HikariConfig();
        hk.setJdbcUrl(cfg.dbUrl);
        hk.setUsername(cfg.dbUser);
        hk.setPassword(cfg.dbPassword);
        hk.setDriverClassName("org.postgresql.Driver");

        hk.setMaximumPoolSize(cfg.dbPoolMax);
        hk.setMinimumIdle(cfg.dbPoolMinIdle);
        hk.setConnectionTimeout(20_000);   // 20 s — fail fast
        hk.setIdleTimeout(300_000);         // 5 min
        hk.setMaxLifetime(1_200_000);       // 20 min
        hk.setKeepaliveTime(60_000);        // 1 min ping
        hk.setConnectionTestQuery("SELECT 1");
        hk.setPoolName("SasooriPool");

        this.dataSource = new HikariDataSource(hk);
        log.info("HikariCP pool initialised — maxPoolSize={}, url={}", cfg.dbPoolMax, cfg.dbUrl);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("HikariCP pool closed");
        }
    }
}
