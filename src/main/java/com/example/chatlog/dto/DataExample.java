// src/main/java/com/example/chatlog/dto/DataExample.java
package com.example.chatlog.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class DataExample {
    private String question;
    private String[] keywords;
    private JsonNode query; // Dùng JsonNode để giữ nguyên cấu trúc JSON

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
}