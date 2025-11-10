package com.example.chatlog.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableJpaRepositories(
    basePackages = {"com.example.chatlog.repository"},
    includeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        value = {com.example.chatlog.repository.AiEmbeddingRepository.class}
    ),
    entityManagerFactoryRef = "secondaryEntityManagerFactory",
    transactionManagerRef = "secondaryTransactionManager"
)
public class SecondaryDataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecondaryDataSourceConfig.class);
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 5000; // 5 seconds
    private static final long MAX_RETRY_DELAY_MS = 30000; // 30 seconds

    @Bean
    @ConfigurationProperties("spring.secondary-datasource")
    public DataSourceProperties secondaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.secondary-datasource.hikari") // ĐỌC TỪ YAML
    public HikariConfig secondaryHikariConfig() {
        return new HikariConfig();
    }

    @Bean
    public DataSource secondaryDataSource() {
        DataSourceProperties properties = secondaryDataSourceProperties();
        
        // Retry logic for connection limit errors
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return createDataSource(properties);
            } catch (Exception e) {
                lastException = e;
                String errorMessage = e.getMessage() != null ? e.getMessage() : "";
                
                if (errorMessage.contains("Max client connections reached")) {
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        // Exponential backoff: 5s, 10s, 20s, 30s, 30s
                        long delay = Math.min(INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1)), MAX_RETRY_DELAY_MS);
                        logger.warn(
                            "Max connections reached. Retry attempt {}/{} in {} seconds... " +
                            "Waiting for idle connections to timeout. " +
                            "Please check Supabase dashboard if this persists.",
                            attempt, MAX_RETRY_ATTEMPTS, delay / 1000
                        );
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while waiting for connection", ie);
                        }
                        continue;
                    } else {
                        logger.error(
                            "Failed to create datasource after {} attempts. " +
                            "Max connections reached. Please:\n" +
                            "1. Wait 2-5 minutes for idle connections to timeout\n" +
                            "2. Check Supabase dashboard → Database → Connection Pooling\n" +
                            "3. Close idle connections manually if needed\n" +
                            "4. Ensure no other application instances are running",
                            MAX_RETRY_ATTEMPTS
                        );
                        throw new IllegalStateException(
                            "Cannot establish connection to Supabase after " + MAX_RETRY_ATTEMPTS + " attempts. " +
                            "Max client connections reached. Please wait a few minutes for connections to timeout " +
                            "or check Supabase dashboard to close idle connections.", e);
                    }
                }
                // For other errors, throw immediately
                throw e;
            }
        }
        
        throw new IllegalStateException("Failed to create datasource after retries", lastException);
    }
    
    private DataSource createDataSource(DataSourceProperties properties) {

        // Validate credentials
        if (properties.getUsername() == null || properties.getUsername().isEmpty() ||
            properties.getUsername().startsWith("${")) {
            throw new IllegalStateException(
                "SECONDARY_DATASOURCE_USERNAME environment variable is not set. " +
                    "Please set it to your Supabase username (format: postgres.[project-ref-id]). " +
                    "Example: postgres.wdxshprlefoixyyuxcwl"
            );
        }
        if (properties.getPassword() == null || properties.getPassword().isEmpty() ||
            properties.getPassword().startsWith("${")) {
            throw new IllegalStateException(
                "SECONDARY_DATASOURCE_PASSWORD environment variable is not set. " +
                    "Please set it to your Supabase password"
            );
        }

        // Validate Supabase username format
        String username = properties.getUsername();
        if (!username.startsWith("postgres.")) {
            throw new IllegalStateException(
                String.format(
                    "Invalid Supabase username format: '%s'. " +
                        "Supabase usernames must be in the format: postgres.[project-ref-id]. " +
                        "Example: postgres.wdxshprlefoixyyuxcwl. " +
                        "You can find your project reference ID in your Supabase project settings.",
                    username
                )
            );
        }

        // SỬ DỤNG CONFIG TỪ YAML
        HikariConfig config = secondaryHikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());

        // Fix prepared statement "already exists" error
        config.addDataSourceProperty("cachePrepStmts", "false");
        config.addDataSourceProperty("prepStmtCacheSize", "0");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "0");

        // Additional Supabase connection pooler optimizations
        config.addDataSourceProperty("socketTimeout", "30");
        config.addDataSourceProperty("loginTimeout", "10");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        
        // Ensure connections are properly validated before use
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        
        // Register MBeans for monitoring (can disable if not needed)
        config.setRegisterMbeans(false);

        return new HikariDataSource(config);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean secondaryEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(secondaryDataSource());

        // Chỉ scan package entity.ai cho AiEmbedding
        em.setPackagesToScan("com.example.chatlog.entity.ai");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(true);
        vendorAdapter.setGenerateDdl(true);
        em.setJpaVendorAdapter(vendorAdapter);

        java.util.Map<String, Object> properties = new java.util.HashMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.ddl-auto", "update");
        properties.put("hibernate.format_sql", "true");
        properties.put("hibernate.jdbc.batch_size", "20");
        properties.put("hibernate.jdbc.fetch_size", "50");
        em.setJpaPropertyMap(properties);

        return em;
    }

    @Bean
    public PlatformTransactionManager secondaryTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(secondaryEntityManagerFactory().getObject());
        return transactionManager;
    }

    // Helper class for secondary datasource properties
    public static class DataSourceProperties {
        private String url;
        private String username;
        private String password;
        private String driverClassName;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    }
}