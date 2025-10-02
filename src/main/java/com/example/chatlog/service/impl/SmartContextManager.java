package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.entity.ChatMessages;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Smart Context Manager - Quản lý ngữ cảnh thông minh
 * Tăng độ chính xác thông qua context-aware processing
 */
@Service
public class SmartContextManager {
    
    private static final int MAX_CONTEXT_MESSAGES = 5;
    private static final Map<String, Double> CONTEXT_WEIGHTS = Map.of(
        "IMMEDIATE_PREVIOUS", 0.8,    // Tin nhắn liền trước
        "SAME_TOPIC", 0.6,           // Cùng chủ đề
        "USER_PATTERN", 0.4,         // Pattern người dùng
        "TIME_CONTEXT", 0.3          // Ngữ cảnh thời gian
    );

    /**
     * Xây dựng context thông minh từ lịch sử chat với error handling
     */
    @Cacheable(value = "session_contexts", keyGenerator = "customKeyGenerator")
    public EnhancedContext buildEnhancedContext(Long sessionId, 
                                                ChatRequest currentRequest, 
                                                List<ChatMessages> chatHistory) {
        
        try {
            System.out.println("[SmartContextManager] Building enhanced context for session " + sessionId + 
                             " with " + (chatHistory != null ? chatHistory.size() : 0) + " history messages");
            
            // 1. Phân tích intent của request hiện tại
            RequestIntent currentIntent = analyzeIntent(currentRequest.message());
            System.out.println("[SmartContextManager] Detected intent: " + currentIntent.type);
            
            // 2. Tìm messages liên quan trong history (safe handling of null/empty history)
            List<ContextMessage> relevantMessages = findRelevantMessages(
                currentRequest, chatHistory != null ? chatHistory : new ArrayList<>(), currentIntent
            );
            System.out.println("[SmartContextManager] Found " + relevantMessages.size() + " relevant messages");
            
            // 3. Trích xuất entities và patterns
            Set<String> extractedEntities = extractEntities(relevantMessages);
            System.out.println("[SmartContextManager] Extracted " + extractedEntities.size() + " entities: " + extractedEntities);
            
            // 4. Xây dựng context prompt
            String contextPrompt = buildContextPrompt(relevantMessages, extractedEntities, currentIntent);
            
            return new EnhancedContext(
                currentIntent,
                relevantMessages,
                extractedEntities,
                contextPrompt
            );
            
        } catch (Exception e) {
            System.out.println("[SmartContextManager] Error building enhanced context: " + e.getMessage());
            e.printStackTrace();
            
            // Return minimal context in case of error
            RequestIntent fallbackIntent = new RequestIntent(IntentType.GENERAL, new HashMap<>());
            return new EnhancedContext(
                fallbackIntent,
                new ArrayList<>(),
                new HashSet<>(),
                "CONTEXT_ERROR: Using fallback context due to: " + e.getMessage()
            );
        }
    }

    /**
     * Phân tích intent của request
     */
    private RequestIntent analyzeIntent(String message) {
        String msg = message.toLowerCase();
        
        // Time-based intents
        if (msg.matches(".*\\b(phút|giờ|ngày|tuần|tháng)\\s+(qua|trước|gần đây).*")) {
            return new RequestIntent(IntentType.TIME_ANALYSIS, extractTimeContext(msg));
        }
        
        // User-focused intents
        if (msg.matches(".*\\b(user|người dùng|tài khoản)\\s+.*")) {
            return new RequestIntent(IntentType.USER_ANALYSIS, extractUserContext(msg));
        }
        
        // Security intents  
        if (msg.matches(".*\\b(chặn|blocked|denied|security|bảo mật).*")) {
            return new RequestIntent(IntentType.SECURITY_ANALYSIS, extractSecurityContext(msg));
        }
        
        // Traffic intents
        if (msg.matches(".*\\b(traffic|lưu lượng|bytes|packets|kết nối).*")) {
            return new RequestIntent(IntentType.TRAFFIC_ANALYSIS, extractTrafficContext(msg));
        }
        
        // Counting intents
        if (msg.matches(".*\\b(tổng|đếm|bao nhiêu|số lượng|count).*")) {
            return new RequestIntent(IntentType.COUNTING, extractCountingContext(msg));
        }
        
        return new RequestIntent(IntentType.GENERAL, Map.of());
    }

    /**
     * Tìm messages liên quan dựa trên semantic similarity và context với safe handling
     */
    private List<ContextMessage> findRelevantMessages(ChatRequest current, 
                                                      List<ChatMessages> history,
                                                      RequestIntent intent) {
        
        if (history == null || history.isEmpty()) {
            System.out.println("[SmartContextManager] No chat history available");
            return new ArrayList<>();
        }
        
        try {
            return history.stream()
                .filter(msg -> msg != null) // Null safety
                .filter(msg -> {
                    try {
                        return msg.getSender() == ChatMessages.SenderType.USER;
                    } catch (Exception e) {
                        System.out.println("[SmartContextManager] ERROR: Could not access sender for message " + 
                                         (msg.getMessageId() != null ? msg.getMessageId() : "unknown") + 
                                         ": " + e.getMessage());
                        e.printStackTrace();
                        return false;
                    }
                })
                .filter(msg -> {
                    try {
                        return msg.getContent() != null && !msg.getContent().trim().isEmpty();
                    } catch (Exception e) {
                        System.out.println("[SmartContextManager] Warning: Could not access content for message");
                        return false;
                    }
                })
                .sorted((a, b) -> {
                    try {
                        if (a.getTimestamp() == null || b.getTimestamp() == null) return 0;
                        return b.getTimestamp().compareTo(a.getTimestamp()); // Sort by recency
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .limit(MAX_CONTEXT_MESSAGES * 2) // Lấy nhiều hơn để filter
                .map(msg -> {
                    try {
                        // Defensive access to message properties
                        String content = msg.getContent();
                        var timestamp = msg.getTimestamp();
                        
                        // Verify session access is working with EAGER fetch
                        Long messageSessionId = msg.getChatSessions() != null ? msg.getChatSessions().getSessionId() : null;
                        System.out.println("[SmartContextManager] ✓ Message " + msg.getMessageId() + 
                                         " session: " + messageSessionId);
                        
                        return new ContextMessage(
                            content,
                            timestamp,
                            calculateRelevanceScore(current.message(), content, intent)
                        );
                    } catch (Exception e) {
                        System.out.println("[SmartContextManager] ERROR creating ContextMessage from message " + 
                                         (msg.getMessageId() != null ? msg.getMessageId() : "unknown") + 
                                         ": " + e.getMessage());
                        e.printStackTrace();
                        return new ContextMessage("", null, 0.0);
                    }
                })
                .filter(ctx -> ctx.relevanceScore > 0.3) // Threshold cho relevance
                .sorted((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore))
                .limit(MAX_CONTEXT_MESSAGES)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            System.out.println("[SmartContextManager] Error processing chat history: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Tính toán relevance score giữa current message và history message
     */
    private double calculateRelevanceScore(String current, String history, RequestIntent intent) {
        double score = 0.0;
        
        // 1. Keyword overlap
        Set<String> currentKeywords = extractKeywords(current);
        Set<String> historyKeywords = extractKeywords(history);
        Set<String> intersection = new HashSet<>(currentKeywords);
        intersection.retainAll(historyKeywords);
        
        if (!currentKeywords.isEmpty()) {
            score += (double) intersection.size() / currentKeywords.size() * 0.4;
        }
        
        // 2. Intent similarity
        RequestIntent historyIntent = analyzeIntent(history);
        if (historyIntent.type == intent.type) {
            score += 0.4;
        }
        
        // 3. Entity overlap (IP addresses, user names, etc.)
        Set<String> currentEntities = extractEntities(List.of(
            new ContextMessage(current, null, 0.0)
        ));
        Set<String> historyEntities = extractEntities(List.of(
            new ContextMessage(history, null, 0.0)
        ));
        Set<String> entityIntersection = new HashSet<>(currentEntities);
        entityIntersection.retainAll(historyEntities);
        
        if (!currentEntities.isEmpty()) {
            score += (double) entityIntersection.size() / currentEntities.size() * 0.2;
        }
        
        return Math.min(score, 1.0);
    }

    /**
     * Trích xuất keywords từ message
     */
    private Set<String> extractKeywords(String message) {
        return Arrays.stream(message.toLowerCase().split("\\s+"))
            .filter(word -> word.length() > 2)
            .filter(word -> !isStopWord(word))
            .collect(Collectors.toSet());
    }

    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of("của", "với", "trong", "và", "có", "là", "được", 
                                      "từ", "đến", "này", "đó", "the", "and", "or", "in", "on");
        return stopWords.contains(word);
    }

    /**
     * Trích xuất entities (IP, user names, time expressions)
     */
    private Set<String> extractEntities(List<ContextMessage> messages) {
        Set<String> entities = new HashSet<>();
        
        for (ContextMessage msg : messages) {
            String content = msg.content;
            
            // IP addresses
            entities.addAll(extractPattern(content, "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"));
            
            // User names (common patterns)
            entities.addAll(extractPattern(content, "\\buser[._-]?\\w+\\b"));
            
            // Time expressions
            entities.addAll(extractPattern(content, "\\b\\d+\\s+(phút|giờ|ngày|tuần|tháng)\\b"));
        }
        
        return entities;
    }

    private Set<String> extractPattern(String text, String regex) {
        return java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(text)
            .results()
            .map(result -> result.group())
            .collect(Collectors.toSet());
    }

    /**
     * Xây dựng context prompt từ relevant messages
     */
    private String buildContextPrompt(List<ContextMessage> relevantMessages,
                                     Set<String> entities,
                                     RequestIntent intent) {
        
        if (relevantMessages.isEmpty()) {
            return "";
        }
        
        StringBuilder contextPrompt = new StringBuilder();
        contextPrompt.append("PREVIOUS CONVERSATION CONTEXT:\n");
        
        for (ContextMessage msg : relevantMessages) {
            contextPrompt.append("- User asked: \"")
                        .append(msg.content.substring(0, Math.min(msg.content.length(), 100)))
                        .append("\" (relevance: ")
                        .append(String.format("%.2f", msg.relevanceScore))
                        .append(")\n");
        }
        
        if (!entities.isEmpty()) {
            contextPrompt.append("\nRELEVANT ENTITIES FROM CONTEXT: ")
                        .append(String.join(", ", entities))
                        .append("\n");
        }
        
        contextPrompt.append("\nCURRENT INTENT: ").append(intent.type).append("\n");
        contextPrompt.append("Use this context to better understand the current question.\n\n");
        
        return contextPrompt.toString();
    }

    // Helper methods for extracting specific contexts
    private Map<String, Object> extractTimeContext(String message) {
        Map<String, Object> context = new HashMap<>();
        // Extract time-related information
        if (message.contains("phút")) context.put("timeUnit", "minutes");
        if (message.contains("giờ")) context.put("timeUnit", "hours");
        if (message.contains("ngày")) context.put("timeUnit", "days");
        return context;
    }

    private Map<String, Object> extractUserContext(String message) {
        Map<String, Object> context = new HashMap<>();
        // Extract user-related patterns
        return context;
    }

    private Map<String, Object> extractSecurityContext(String message) {
        Map<String, Object> context = new HashMap<>();
        // Extract security-related patterns  
        return context;
    }

    private Map<String, Object> extractTrafficContext(String message) {
        Map<String, Object> context = new HashMap<>();
        // Extract traffic-related patterns
        return context;
    }

    private Map<String, Object> extractCountingContext(String message) {
        Map<String, Object> context = new HashMap<>();
        // Extract counting-related patterns
        return context;
    }

    // Data classes
    public static class EnhancedContext {
        public final RequestIntent intent;
        public final List<ContextMessage> relevantMessages;
        public final Set<String> entities;
        public final String contextPrompt;

        public EnhancedContext(RequestIntent intent, List<ContextMessage> relevantMessages,
                              Set<String> entities, String contextPrompt) {
            this.intent = intent;
            this.relevantMessages = relevantMessages;
            this.entities = entities;
            this.contextPrompt = contextPrompt;
        }
    }

    public static class ContextMessage {
        public final String content;
        public final java.time.LocalDateTime timestamp;
        public final double relevanceScore;

        public ContextMessage(String content, java.time.LocalDateTime timestamp, double relevanceScore) {
            this.content = content;
            this.timestamp = timestamp;
            this.relevanceScore = relevanceScore;
        }
    }

    public static class RequestIntent {
        public final IntentType type;
        public final Map<String, Object> context;

        public RequestIntent(IntentType type, Map<String, Object> context) {
            this.type = type;
            this.context = context;
        }
    }

    public enum IntentType {
        TIME_ANALYSIS, USER_ANALYSIS, SECURITY_ANALYSIS, TRAFFIC_ANALYSIS, COUNTING, GENERAL
    }
}