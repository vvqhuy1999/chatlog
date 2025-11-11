// src/main/java/com/example/chatlog/dto/DataExample.java
package com.example.chatlog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataExample {
    private String question;
    private String[] keywords;
    private JsonNode query; // Dùng JsonNode để giữ nguyên cấu trúc JSON
    
    // New fields from enhanced JSON knowledge base files
    private String scenario;
    private String phase;
    private String businessValue;
    
    // Additional fields for different security scenarios
    private String complianceType;
    private String securityFramework;
    private String operationalGoal;

    // --- Bắt buộc phải có Getters và Setters cho Jackson ---

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String[] getKeywords() {
        return keywords;
    }

    public void setKeywords(String[] keywords) {
        this.keywords = keywords;
    }

    public JsonNode getQuery() {
        return query;
    }

    public void setQuery(JsonNode query) {
        this.query = query;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getBusinessValue() {
        return businessValue;
    }

    public void setBusinessValue(String businessValue) {
        this.businessValue = businessValue;
    }

    public String getComplianceType() {
        return complianceType;
    }

    public void setComplianceType(String complianceType) {
        this.complianceType = complianceType;
    }

    public String getSecurityFramework() {
        return securityFramework;
    }

    public void setSecurityFramework(String securityFramework) {
        this.securityFramework = securityFramework;
    }

    public String getOperationalGoal() {
        return operationalGoal;
    }

    public void setOperationalGoal(String operationalGoal) {
        this.operationalGoal = operationalGoal;
    }
}