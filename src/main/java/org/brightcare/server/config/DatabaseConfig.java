package org.brightcare.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton database configuration using HikariCP connection pool.
 * <p>
 * Reads connection parameters from {@code application.properties} on the classpath.
 * The pool is lazily initialised on first call to {@link #getDataSource()}.
 */
public final class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    private static final String PROPERTIES_FILE = "application.properties";

    private static volatile HikariDataSource dataSource;
    private static final Object LOCK = new Object();

    private DatabaseConfig() {
        // singleton — prevent instantiation
    }

    /**
     * Get the HikariCP data source, initialising it if necessary.
     *
     * @return the pooled data source
     * @throws RuntimeException if the properties file cannot be loaded
     *                           or the pool cannot be created
     */
    public static HikariDataSource getDataSource() {
        if (dataSource == null) {
            synchronized (LOCK) {
                if (dataSource == null) {
                    dataSource = initDataSource();
                }
            }
        }
        return dataSource;
    }

    /**
     * Get a database connection from the pool.
     * Callers MUST close the returned connection (returns it to the pool).
     *
     * @return a JDBC connection
     * @throws SQLException if a connection cannot be obtained
     */
    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    /**
     * Shut down the connection pool gracefully.
     * Should be called when the server is stopping.
     */
    public static void shutdown() {
        synchronized (LOCK) {
            if (dataSource != null && !dataSource.isClosed()) {
                log.info("Shutting down HikariCP connection pool...");
                dataSource.close();
                dataSource = null;
                log.info("Connection pool shut down successfully");
            }
        }
    }

    // -------------------------------------------------------------------------

    private static HikariDataSource initDataSource() {
        Properties props = loadProperties();

        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(props.getProperty("db.url"));
        config.setUsername(props.getProperty("db.username"));
        config.setPassword(props.getProperty("db.password"));

        // Pool tuning
        config.setMaximumPoolSize(
                Integer.parseInt(props.getProperty("db.pool.maxSize", "10")));
        config.setMinimumIdle(
                Integer.parseInt(props.getProperty("db.pool.minIdle", "2")));
        config.setConnectionTimeout(
                Long.parseLong(props.getProperty("db.pool.connectionTimeoutMs", "30000")));
        config.setIdleTimeout(
                Long.parseLong(props.getProperty("db.pool.idleTimeoutMs", "600000")));
        config.setMaxLifetime(
                Long.parseLong(props.getProperty("db.pool.maxLifetimeMs", "1800000")));

        // Leak detection (helpful during development)
        config.setLeakDetectionThreshold(
                Long.parseLong(props.getProperty("db.pool.leakDetectionThresholdMs", "60000")));

        // Recommended PostgreSQL settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        // Validate connections
        config.setConnectionTestQuery("SELECT 1");

        log.info("HikariCP pool initialised — jdbcUrl={}, maxPoolSize={}",
                config.getJdbcUrl(), config.getMaximumPoolSize());

        return new HikariDataSource(config);
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = DatabaseConfig.class.getClassLoader()
                .getResourceAsStream(PROPERTIES_FILE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Cannot find " + PROPERTIES_FILE + " on the classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load " + PROPERTIES_FILE, e);
        }
        return props;
    }
}
