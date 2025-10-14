package com.example.chatlog.service.impl;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Performance Monitoring Service
 * Theo dõi và tối ưu hóa performance của hệ thống
 */
@Service
public class PerformanceMonitoringService {
    
    private final Map<String, AtomicLong> responseTimesMs = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final AtomicReference<PerformanceSnapshot> lastSnapshot = new AtomicReference<>();
    
    /**
     * Ghi nhận metrics của một request
     */
    public void recordRequest(String endpoint, long responseTimeMs, boolean success) {
        responseTimesMs.computeIfAbsent(endpoint, k -> new AtomicLong()).addAndGet(responseTimeMs);
        requestCounts.computeIfAbsent(endpoint, k -> new AtomicLong()).incrementAndGet();
        
        if (!success) {
            errorCounts.computeIfAbsent(endpoint, k -> new AtomicLong()).incrementAndGet();
        }
    }
    
    /**
     * Lấy performance snapshot hiện tại
     */
    public PerformanceSnapshot getCurrentSnapshot() {
        Map<String, EndpointMetrics> endpointMetrics = new HashMap<>();
        
        for (String endpoint : requestCounts.keySet()) {
            long totalRequests = requestCounts.get(endpoint).get();
            long totalResponseTime = responseTimesMs.get(endpoint).get();
            long totalErrors = errorCounts.getOrDefault(endpoint, new AtomicLong()).get();
            
            double avgResponseTime = totalRequests > 0 ? (double) totalResponseTime / totalRequests : 0;
            double errorRate = totalRequests > 0 ? (double) totalErrors / totalRequests : 0;
            
            endpointMetrics.put(endpoint, new EndpointMetrics(
                totalRequests, avgResponseTime, errorRate, totalErrors
            ));
        }
        
        PerformanceSnapshot snapshot = new PerformanceSnapshot(
            System.currentTimeMillis(),
            endpointMetrics
        );
        
        lastSnapshot.set(snapshot);
        return snapshot;
    }
    
    /**
     * Phân tích performance trends
     */
    public PerformanceAnalysis analyzePerformance() {
        PerformanceSnapshot current = getCurrentSnapshot();
        List<String> recommendations = new ArrayList<>();
        List<String> alerts = new ArrayList<>();
        
        for (Map.Entry<String, EndpointMetrics> entry : current.endpointMetrics.entrySet()) {
            String endpoint = entry.getKey();
            EndpointMetrics metrics = entry.getValue();
            
            // Performance alerts
            if (metrics.avgResponseTimeMs > 5000) {
                alerts.add("SLOW_RESPONSE: " + endpoint + " - Avg response time: " + 
                          String.format("%.0fms", metrics.avgResponseTimeMs));
            }
            
            if (metrics.errorRate > 0.1) {
                alerts.add("HIGH_ERROR_RATE: " + endpoint + " - Error rate: " + 
                          String.format("%.1f%%", metrics.errorRate * 100));
            }
            
            // Performance recommendations
            if (metrics.avgResponseTimeMs > 2000 && metrics.avgResponseTimeMs <= 5000) {
                recommendations.add("OPTIMIZE: " + endpoint + " - Consider adding caching");
            }
            
            if (metrics.totalRequests > 1000 && metrics.errorRate < 0.01) {
                recommendations.add("STABLE: " + endpoint + " - Performance is stable");
            }
        }
        
        return new PerformanceAnalysis(current, recommendations, alerts);
    }
    

    
    // Data classes
    public static class PerformanceSnapshot {
        public final long timestamp;
        public final Map<String, EndpointMetrics> endpointMetrics;
        
        public PerformanceSnapshot(long timestamp, Map<String, EndpointMetrics> endpointMetrics) {
            this.timestamp = timestamp;
            this.endpointMetrics = new HashMap<>(endpointMetrics);
        }
    }
    
    public static class EndpointMetrics {
        public final long totalRequests;
        public final double avgResponseTimeMs;
        public final double errorRate;
        public final long totalErrors;
        
        public EndpointMetrics(long totalRequests, double avgResponseTimeMs, 
                              double errorRate, long totalErrors) {
            this.totalRequests = totalRequests;
            this.avgResponseTimeMs = avgResponseTimeMs;
            this.errorRate = errorRate;
            this.totalErrors = totalErrors;
        }
    }
    
    public static class PerformanceAnalysis {
        public final PerformanceSnapshot snapshot;
        public final List<String> recommendations;
        public final List<String> alerts;
        
        public PerformanceAnalysis(PerformanceSnapshot snapshot, 
                                  List<String> recommendations, 
                                  List<String> alerts) {
            this.snapshot = snapshot;
            this.recommendations = new ArrayList<>(recommendations);
            this.alerts = new ArrayList<>(alerts);
        }
    }
}