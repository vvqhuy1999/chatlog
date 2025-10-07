package com.example.chatlog.service.impl;

import com.example.chatlog.dto.DataExample;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Enhanced Example Matching Service
 * Cải thiện thuật toán matching examples với semantic analysis và context awareness
 */
@Service
public class EnhancedExampleMatchingService {
    
    // Từ đồng nghĩa và related terms cho tiếng Việt
    private static final Map<String, List<String>> SEMANTIC_SYNONYMS = Map.of(
        "tìm", Arrays.asList("tìm kiếm", "search", "find", "tra cứu", "lookup"),
        "chặn", Arrays.asList("block", "deny", "cấm", "ngăn chặn", "từ chối"),
        "nhiều", Arrays.asList("most", "max", "cao nhất", "lớn nhất", "top"),
        "ip", Arrays.asList("địa chỉ ip", "source.ip", "destination.ip", "client"),
        "user", Arrays.asList("người dùng", "tài khoản", "account", "username"),
        "thời gian", Arrays.asList("time", "timestamp", "giờ", "ngày", "now"),
        "lưu lượng", Arrays.asList("traffic", "bandwidth", "bytes", "packets"),
        "bảo mật", Arrays.asList("security", "firewall", "attack", "threat")
    );
    
    // Domain keywords và weight
    private static final Map<String, Double> DOMAIN_WEIGHTS = Map.of(
        "security", 1.5,
        "network", 1.3,
        "user_activity", 1.2,
        "performance", 1.1,
        "general", 1.0
    );
    
    // Time context keywords
    private static final Set<String> TIME_KEYWORDS = Set.of(
        "giờ", "ngày", "tuần", "tháng", "phút", "giây",
        "hour", "day", "week", "month", "minute", "second",
        "now", "past", "recent", "latest"
    );

    /**
     * Find relevant examples with enhanced scoring algorithm
     */
    @Cacheable(value = "enhanced_examples", keyGenerator = "customKeyGenerator")
    public List<DataExample> findRelevantExamples(String userQuery, List<DataExample> exampleLibrary) {
        System.out.println("🔍 Query matching: \"" + userQuery + "\"");
        
        if (exampleLibrary == null || exampleLibrary.isEmpty()) {
            System.out.println("❌ Knowledge base is empty");
            return new ArrayList<>();
        }
        
        // Step 1: Advanced keyword extraction
        QueryAnalysis queryAnalysis = analyzeQuery(userQuery);
        
        // Step 2: Score all examples with enhanced algorithm
        List<ExampleScore> scoredExamples = new ArrayList<>();
        
        for (int i = 0; i < exampleLibrary.size(); i++) {
            DataExample example = exampleLibrary.get(i);
            if (example.getKeywords() == null) continue;
            
            ExampleScore score = calculateEnhancedScore(queryAnalysis, example, i + 1);
            if (score.getTotalScore() > 0) {
                scoredExamples.add(score);
            }
        }
        
        // Step 3: Sort by enhanced score and apply diversity filter
        List<DataExample> finalResults = scoredExamples.stream()
                .sorted((s1, s2) -> Double.compare(s2.getTotalScore(), s1.getTotalScore()))
                .limit(10) // Get top 10 first
                .map(ExampleScore::getExample)
                .collect(Collectors.toList());
        
        // Apply diversity filter to avoid similar examples
        List<DataExample> diverseResults = applyDiversityFilter(finalResults, 10);
        
        System.out.println("✅ Found " + diverseResults.size() + " relevant examples");
        return diverseResults;
    }

    /**
     * Analyze query to extract semantic information
     */
    private QueryAnalysis analyzeQuery(String query) {
        String queryLower = query.toLowerCase();
        
        // Extract primary keywords
        List<String> primaryKeywords = Arrays.stream(queryLower.split("\\s+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !isStopWord(word))
                .collect(Collectors.toList());
        
        // Extract semantic keywords using synonyms
        Set<String> semanticKeywords = new HashSet<>();
        for (String keyword : primaryKeywords) {
            semanticKeywords.add(keyword);
            // Add synonyms
            for (Map.Entry<String, List<String>> entry : SEMANTIC_SYNONYMS.entrySet()) {
                if (entry.getValue().contains(keyword) || entry.getKey().equals(keyword)) {
                    semanticKeywords.addAll(entry.getValue());
                    semanticKeywords.add(entry.getKey());
                }
            }
        }
        
        // Detect domain
        String domain = detectDomain(queryLower);
        
        // Detect intent
        String intent = detectIntent(queryLower);
        
        // Check time context
        boolean hasTimeContext = TIME_KEYWORDS.stream()
                .anyMatch(queryLower::contains);
        
        return new QueryAnalysis(primaryKeywords, new ArrayList<>(semanticKeywords), 
                               domain, intent, hasTimeContext);
    }

    /**
     * Calculate enhanced score for an example
     */
    private ExampleScore calculateEnhancedScore(QueryAnalysis queryAnalysis, DataExample example, int exampleIndex) {
        double semanticScore = calculateSemanticScore(queryAnalysis, example);
        double contextScore = calculateContextScore(queryAnalysis, example);
        double domainScore = calculateDomainScore(queryAnalysis, example);
        double diversityBonus = 0.0; // Will be calculated later if needed
        
        double totalScore = semanticScore + contextScore + domainScore + diversityBonus;
        
        return new ExampleScore(example, semanticScore, contextScore, domainScore, diversityBonus, totalScore);
    }

    /**
     * Calculate semantic similarity score
     */
    private double calculateSemanticScore(QueryAnalysis queryAnalysis, DataExample example) {
        double score = 0.0;
        List<String> matchedKeywords = new ArrayList<>();
        
        for (String exampleKeyword : Arrays.asList(example.getKeywords())) {
            String exampleKeywordLower = exampleKeyword.toLowerCase();
            
            // Exact match (highest weight)
            for (String queryKeyword : queryAnalysis.getPrimaryKeywords()) {
                if (exampleKeywordLower.equals(queryKeyword)) {
                    score += 3.0;
                    matchedKeywords.add(exampleKeyword + "(exact)");
                }
            }
            
            // Semantic match (medium weight)
            for (String semanticKeyword : queryAnalysis.getSemanticKeywords()) {
                if (exampleKeywordLower.contains(semanticKeyword) || semanticKeyword.contains(exampleKeywordLower)) {
                    score += 2.0;
                    matchedKeywords.add(exampleKeyword + "(semantic)");
                }
            }
            
            // Partial match (lower weight)
            for (String queryKeyword : queryAnalysis.getPrimaryKeywords()) {
                if (exampleKeywordLower.contains(queryKeyword) || queryKeyword.contains(exampleKeywordLower)) {
                    score += 1.0;
                    matchedKeywords.add(exampleKeyword + "(partial)");
                }
            }
        }
        
        
        return score;
    }

    /**
     * Calculate context relevance score
     */
    private double calculateContextScore(QueryAnalysis queryAnalysis, DataExample example) {
        double score = 0.0;
        
        // Time context matching
        if (queryAnalysis.hasTimeContext()) {
            boolean exampleHasTime = Arrays.stream(example.getKeywords())
                    .anyMatch(keyword -> TIME_KEYWORDS.stream()
                            .anyMatch(timeKeyword -> keyword.toLowerCase().contains(timeKeyword)));
            if (exampleHasTime) {
                score += 2.0;
            }
        }
        
        // Intent matching
        if (queryAnalysis.getIntent() != null) {
            String exampleIntent = detectExampleIntent(example);
            if (queryAnalysis.getIntent().equals(exampleIntent)) {
                score += 1.5;
            }
        }
        
        return score;
    }

    /**
     * Calculate domain relevance score
     */
    private double calculateDomainScore(QueryAnalysis queryAnalysis, DataExample example) {
        double score = 0.0;
        String queryDomain = queryAnalysis.getDomain();
        String exampleDomain = detectExampleDomain(example);
        
        if (queryDomain.equals(exampleDomain)) {
            double weight = DOMAIN_WEIGHTS.getOrDefault(queryDomain, 1.0);
            score = 1.0 * weight;
        }
        
        return score;
    }

    /**
     * Apply diversity filter to avoid similar examples
     */
    private List<DataExample> applyDiversityFilter(List<DataExample> examples, int maxResults) {
        if (examples.size() <= maxResults) {
            return examples;
        }
        
        List<DataExample> diverseExamples = new ArrayList<>();
        diverseExamples.add(examples.get(0)); // Always include top result
        
        for (DataExample candidate : examples.subList(1, examples.size())) {
            boolean isDiverse = true;
            
            for (DataExample selected : diverseExamples) {
                double similarity = calculateExampleSimilarity(candidate, selected);
                if (similarity > 0.7) { // Too similar
                    isDiverse = false;
                    break;
                }
            }
            
            if (isDiverse) {
                diverseExamples.add(candidate);
                if (diverseExamples.size() >= maxResults) {
                    break;
                }
            }
        }
        
        System.out.println("🔄 Diversity filter: " + examples.size() + " → " + diverseExamples.size() + " examples");
        return diverseExamples;
    }

    /**
     * Calculate similarity between two examples
     */
    private double calculateExampleSimilarity(DataExample example1, DataExample example2) {
        Set<String> keywords1 = new HashSet<>(Arrays.asList(example1.getKeywords()));
        Set<String> keywords2 = new HashSet<>(Arrays.asList(example2.getKeywords()));
        
        Set<String> intersection = new HashSet<>(keywords1);
        intersection.retainAll(keywords2);
        
        Set<String> union = new HashSet<>(keywords1);
        union.addAll(keywords2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    // Helper methods
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of("các", "của", "trong", "và", "có", "là", "với", "để", "này", "đó");
        return stopWords.contains(word);
    }

    private String detectDomain(String query) {
        if (query.contains("chặn") || query.contains("deny") || query.contains("block") || query.contains("security")) {
            return "security";
        } else if (query.contains("traffic") || query.contains("bytes") || query.contains("network")) {
            return "network";
        } else if (query.contains("user") || query.contains("người dùng") || query.contains("login")) {
            return "user_activity";
        } else if (query.contains("performance") || query.contains("slow") || query.contains("fast")) {
            return "performance";
        }
        return "general";
    }

    private String detectIntent(String query) {
        if (query.contains("tổng") || query.contains("đếm") || query.contains("bao nhiêu") || query.contains("count")) {
            return "counting";
        } else if (query.contains("top") || query.contains("nhiều nhất") || query.contains("cao nhất")) {
            return "ranking";
        } else if (query.contains("tìm") || query.contains("search") || query.contains("find")) {
            return "search";
        } else if (query.contains("phân tích") || query.contains("analysis") || query.contains("trend")) {
            return "analysis";
        }
        return "general";
    }

    private String detectExampleIntent(DataExample example) {
        String question = example.getQuestion().toLowerCase();
        return detectIntent(question);
    }

    private String detectExampleDomain(DataExample example) {
        String question = example.getQuestion().toLowerCase();
        return detectDomain(question);
    }

    // Inner classes
    public static class QueryAnalysis {
        private final List<String> primaryKeywords;
        private final List<String> semanticKeywords;
        private final String domain;
        private final String intent;
        private final boolean hasTimeContext;

        public QueryAnalysis(List<String> primaryKeywords, List<String> semanticKeywords, 
                           String domain, String intent, boolean hasTimeContext) {
            this.primaryKeywords = primaryKeywords;
            this.semanticKeywords = semanticKeywords;
            this.domain = domain;
            this.intent = intent;
            this.hasTimeContext = hasTimeContext;
        }

        // Getters
        public List<String> getPrimaryKeywords() { return primaryKeywords; }
        public List<String> getSemanticKeywords() { return semanticKeywords; }
        public String getDomain() { return domain; }
        public String getIntent() { return intent; }
        public boolean hasTimeContext() { return hasTimeContext; }
    }

    public static class ExampleScore {
        private final DataExample example;
        private final double semanticScore;
        private final double contextScore;
        private final double domainScore;
        private final double diversityBonus;
        private final double totalScore;

        public ExampleScore(DataExample example, double semanticScore, double contextScore, 
                           double domainScore, double diversityBonus, double totalScore) {
            this.example = example;
            this.semanticScore = semanticScore;
            this.contextScore = contextScore;
            this.domainScore = domainScore;
            this.diversityBonus = diversityBonus;
            this.totalScore = totalScore;
        }

        // Getters
        public DataExample getExample() { return example; }
        public double getSemanticScore() { return semanticScore; }
        public double getContextScore() { return contextScore; }
        public double getDomainScore() { return domainScore; }
        public double getDiversityBonus() { return diversityBonus; }
        public double getTotalScore() { return totalScore; }
    }
}