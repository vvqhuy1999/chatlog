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

    @Bean
    @ConfigurationProperties("spring.secondary-datasource")
    public DataSourceProperties secondaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource secondaryDataSource() {
        DataSourceProperties properties = secondaryDataSourceProperties();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(3);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setAutoCommit(true);
        
        // Fix prepared statement "already exists" error
        config.addDataSourceProperty("cachePrepStmts", "false");
        config.addDataSourceProperty("prepStmtCacheSize", "0");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "0");
        
        return new HikariDataSource(config);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean secondaryEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(secondaryDataSource());
        
        // Chỉ scan package entity.ai cho AiEmbedding
        em.setPackagesToScan("com.example.chatlog.entity.ai");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(true);  // Enable to see SQL queries
        vendorAdapter.setGenerateDdl(true);  // Enable DDL for Supabase (has pgvector)
        em.setJpaVendorAdapter(vendorAdapter);

        java.util.Map<String, Object> properties = new java.util.HashMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.ddl-auto", "update");  // Enable DDL update for Secondary DB (Supabase with pgvector)
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
