package com.example.chatlog.controller;

import com.example.chatlog.service.impl.PerformanceMonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller cho monitoring và performance metrics
 */
@RestController
@RequestMapping("/api/monitoring")
@CrossOrigin(origins = "*")
public class MonitoringController {

    @Autowired
    private PerformanceMonitoringService performanceMonitoringService;

    /**
     * Lấy performance snapshot hiện tại
     */
    @GetMapping("/performance")
    public ResponseEntity<PerformanceMonitoringService.PerformanceSnapshot> getPerformanceSnapshot() {
        try {
            PerformanceMonitoringService.PerformanceSnapshot snapshot = 
                performanceMonitoringService.getCurrentSnapshot();
            return ResponseEntity.ok(snapshot);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Lấy performance analysis và recommendations
     */
    @GetMapping("/analysis")
    public ResponseEntity<PerformanceMonitoringService.PerformanceAnalysis> getPerformanceAnalysis() {
        try {
            PerformanceMonitoringService.PerformanceAnalysis analysis = 
                performanceMonitoringService.analyzePerformance();
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Lấy performance report dạng text
     */
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getPerformanceReport() {
        try {
            String report = performanceMonitoringService.generatePerformanceReport();
            Map<String, Object> response = new HashMap<>();
            response.put("report", report);
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to generate performance report: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Reset performance metrics (admin only)
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetMetrics() {
        try {
            performanceMonitoringService.resetMetrics();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Performance metrics reset successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to reset metrics: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "chatlog-monitoring");
        return ResponseEntity.ok(health);
    }
}