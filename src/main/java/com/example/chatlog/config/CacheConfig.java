package com.example.chatlog.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Method;
import java.util.StringJoiner;
import java.util.Collection;

/**
 * Cache Configuration cho chatlog system
 * Tối ưu performance với multi-level caching
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
            "elasticsearch_queries",    // Cache Elasticsearch queries
            "ai_responses",             // Cache AI responses
            "schema_mappings",          // Cache Elasticsearch schema
            "query_patterns",           // Cache query patterns
            "session_contexts",         // Cache session contexts
            "enhanced_examples",        // Cache enhanced example matching results
            "query_analysis"            // Cache query analysis results
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void clearCacheOnStartup(ApplicationReadyEvent event) {
        CacheManager cacheManager = event.getApplicationContext().getBean(CacheManager.class);
        if (cacheManager != null) {
            Collection<String> cacheNames = cacheManager.getCacheNames();
            logger.info("🔄 [CacheConfig] Clearing all caches on application startup: {}", cacheNames);
            for (String cacheName : cacheNames) {
                org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            }
            logger.info("✅ [CacheConfig] All caches cleared successfully");
        }
    }

    @Bean("customKeyGenerator")
    public KeyGenerator keyGenerator() {
        return new CustomKeyGenerator();
    }

    public static class CustomKeyGenerator implements KeyGenerator {
        @Override
        public Object generate(Object target, Method method, Object... params) {
            StringJoiner joiner = new StringJoiner(":");
            joiner.add(method.getName());
            
            for (Object param : params) {
                if (param != null) {
                    // Hash long strings để tránh cache key quá dài
                    String paramStr = param.toString();
                    if (paramStr.length() > 100) {
                        joiner.add(String.valueOf(paramStr.hashCode()));
                    } else {
                        joiner.add(paramStr);
                    }
                }
            }
            return joiner.toString();
        }
    }
}