package com.example.chatlog.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.interceptor.KeyGenerator;
import java.lang.reflect.Method;
import java.util.StringJoiner;

/**
 * Cache Configuration cho chatlog system
 * Tối ưu performance với multi-level caching
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
            "elasticsearch_queries",    // Cache Elasticsearch queries
            "ai_responses",             // Cache AI responses
            "schema_mappings",          // Cache Elasticsearch schema
            "query_patterns",           // Cache query patterns
            "session_contexts"          // Cache session contexts
        );
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