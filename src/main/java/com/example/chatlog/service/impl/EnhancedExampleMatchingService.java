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

    // Từ đồng nghĩa và các thuật ngữ liên quan cho tiếng Việt
    private static final Map<String, List<String>> SEMANTIC_SYNONYMS = Map.of(
        "tìm", Arrays.asList("tìm kiếm", "search", "find", "tra cứu", "lookup", "liệt kê"),
        "chặn", Arrays.asList("block", "deny", "cấm", "ngăn chặn", "từ chối"),
        "nhiều", Arrays.asList("most", "max", "cao nhất", "lớn nhất", "top"),
        "ip", Arrays.asList("địa chỉ ip", "source.ip", "destination.ip", "client"),
        "user", Arrays.asList("người dùng", "tài khoản", "account", "username"),
        "thời gian", Arrays.asList("time", "timestamp", "giờ", "ngày", "now", "khi nào"),
        "lưu lượng", Arrays.asList("traffic", "băng thông", "bandwidth", "bytes", "packets"),
        "bảo mật", Arrays.asList("security", "firewall", "attack", "threat", "tấn công", "hiểm họa")
    );

    // Trọng số cho các lĩnh vực (domain)
    private static final Map<String, Double> DOMAIN_WEIGHTS = Map.of(
        "security", 1.5,
        "network", 1.3,
        "user_activity", 1.2,
        "performance", 1.1,
        "general", 1.0
    );

    // Từ khóa liên quan đến thời gian
    private static final Set<String> TIME_KEYWORDS = Set.of(
        "giờ", "ngày", "tuần", "tháng", "phút", "giây",
        "hour", "day", "week", "month", "minute", "second",
        "now", "past", "recent", "latest", "hôm nay", "hôm qua"
    );

    /**
     * Tìm các ví dụ liên quan với thuật toán tính điểm nâng cao
     */
    @Cacheable(value = "enhanced_examples", keyGenerator = "customKeyGenerator")
    public List<DataExample> findRelevantExamples(String userQuery, List<DataExample> exampleLibrary) {
        System.out.println("🔍 Bắt đầu quá trình so khớp thông minh cho câu hỏi: \"" + userQuery + "\"");

        if (exampleLibrary == null || exampleLibrary.isEmpty()) {
            System.out.println("❌ Kho tri thức (knowledge base) trống.");
            return new ArrayList<>();
        }

        // Bước 1: Phân tích câu hỏi người dùng để trích xuất thông tin ngữ nghĩa
        QueryAnalysis queryAnalysis = analyzeQuery(userQuery);

        // Bước 2: Tính điểm cho tất cả các ví dụ trong kho tri thức
        List<ExampleScore> scoredExamples = new ArrayList<>();

        for (int i = 0; i < exampleLibrary.size(); i++) {
            DataExample example = exampleLibrary.get(i);
            if (example.getKeywords() == null) continue;

            ExampleScore score = calculateEnhancedScore(queryAnalysis, example, i + 1);
            if (score.getTotalScore() > 0) {
                scoredExamples.add(score);
            }
        }

        // Bước 3: Sắp xếp theo điểm số và áp dụng bộ lọc đa dạng
        List<DataExample> finalResults = scoredExamples.stream()
            .sorted((s1, s2) -> Double.compare(s2.getTotalScore(), s1.getTotalScore()))
            .limit(10) // Lấy top 10 kết quả đầu tiên
            .map(ExampleScore::getExample)
            .collect(Collectors.toList());

        // Áp dụng bộ lọc đa dạng để tránh các ví dụ quá giống nhau
        List<DataExample> diverseResults = applyDiversityFilter(finalResults, 5); // Giới hạn 5 ví dụ cuối cùng

        System.out.println("✅ Tìm thấy " + diverseResults.size() + " ví dụ phù hợp và đa dạng.");
        return diverseResults;
    }

    /**
     * Phân tích câu hỏi để trích xuất thông tin ngữ nghĩa
     */
    private QueryAnalysis analyzeQuery(String query) {
        String queryLower = query.toLowerCase();

        // Trích xuất từ khóa chính
        List<String> primaryKeywords = Arrays.stream(queryLower.split("\\s+"))
            .filter(word -> word.length() > 2)
            .filter(word -> !isStopWord(word))
            .collect(Collectors.toList());

        // Mở rộng từ khóa ngữ nghĩa bằng từ đồng nghĩa
        Set<String> semanticKeywords = new HashSet<>();
        for (String keyword : primaryKeywords) {
            semanticKeywords.add(keyword);
            for (Map.Entry<String, List<String>> entry : SEMANTIC_SYNONYMS.entrySet()) {
                if (entry.getValue().contains(keyword) || entry.getKey().equals(keyword)) {
                    semanticKeywords.addAll(entry.getValue());
                    semanticKeywords.add(entry.getKey());
                }
            }
        }

        // Phát hiện lĩnh vực (domain)
        String domain = detectDomain(queryLower);

        // Phát hiện ý định (intent)
        String intent = detectIntent(queryLower);

        // Kiểm tra ngữ cảnh thời gian
        boolean hasTimeContext = TIME_KEYWORDS.stream()
            .anyMatch(queryLower::contains);

        return new QueryAnalysis(primaryKeywords, new ArrayList<>(semanticKeywords),
            domain, intent, hasTimeContext);
    }

    /**
     * Tính điểm nâng cao cho một ví dụ
     */
    private ExampleScore calculateEnhancedScore(QueryAnalysis queryAnalysis, DataExample example, int exampleIndex) {
        double semanticScore = calculateSemanticScore(queryAnalysis, example);
        double contextScore = calculateContextScore(queryAnalysis, example);
        double domainScore = calculateDomainScore(queryAnalysis, example);

        double totalScore = semanticScore + contextScore + domainScore;

        return new ExampleScore(example, semanticScore, contextScore, domainScore, totalScore);
    }

    /**
     * Tính điểm tương đồng ngữ nghĩa
     */
    private double calculateSemanticScore(QueryAnalysis queryAnalysis, DataExample example) {
        double score = 0.0;

        for (String exampleKeyword : Arrays.asList(example.getKeywords())) {
            String exampleKeywordLower = exampleKeyword.toLowerCase();

            // So khớp chính xác (trọng số cao nhất)
            if (queryAnalysis.getPrimaryKeywords().stream().anyMatch(exampleKeywordLower::equals)) {
                score += 3.0;
            }

            // So khớp ngữ nghĩa (trọng số trung bình)
            else if (queryAnalysis.getSemanticKeywords().stream().anyMatch(semKey -> exampleKeywordLower.contains(semKey) || semKey.contains(exampleKeywordLower))) {
                score += 2.0;
            }

            // So khớp một phần (trọng số thấp)
            else if (queryAnalysis.getPrimaryKeywords().stream().anyMatch(primKey -> exampleKeywordLower.contains(primKey) || primKey.contains(exampleKeywordLower))) {
                score += 1.0;
            }
        }

        return score;
    }

    /**
     * Tính điểm liên quan về ngữ cảnh
     */
    private double calculateContextScore(QueryAnalysis queryAnalysis, DataExample example) {
        double score = 0.0;

        // So khớp ngữ cảnh thời gian
        if (queryAnalysis.hasTimeContext()) {
            boolean exampleHasTime = Arrays.stream(example.getKeywords())
                .anyMatch(keyword -> TIME_KEYWORDS.stream()
                    .anyMatch(timeKeyword -> keyword.toLowerCase().contains(timeKeyword)));
            if (exampleHasTime) {
                score += 2.0;
            }
        }

        // So khớp ý định
        String exampleIntent = detectIntent(example.getQuestion().toLowerCase());
        if (queryAnalysis.getIntent().equals(exampleIntent)) {
            score += 1.5;
        }

        return score;
    }

    /**
     * Tính điểm liên quan về lĩnh vực
     */
    private double calculateDomainScore(QueryAnalysis queryAnalysis, DataExample example) {
        String queryDomain = queryAnalysis.getDomain();
        String exampleDomain = detectDomain(example.getQuestion().toLowerCase());

        if (queryDomain.equals(exampleDomain)) {
            return 1.0 * DOMAIN_WEIGHTS.getOrDefault(queryDomain, 1.0);
        }

        return 0.0;
    }

    /**
     * Áp dụng bộ lọc đa dạng để tránh các ví dụ trùng lặp
     */
    private List<DataExample> applyDiversityFilter(List<DataExample> examples, int maxResults) {
        if (examples.size() <= maxResults) {
            return examples;
        }

        List<DataExample> diverseExamples = new ArrayList<>();
        if (!examples.isEmpty()) {
            diverseExamples.add(examples.get(0)); // Luôn giữ lại kết quả tốt nhất
        }

        for (DataExample candidate : examples.subList(1, examples.size())) {
            if (diverseExamples.size() >= maxResults) {
                break;
            }

            boolean isDiverseEnough = diverseExamples.stream()
                .allMatch(selected -> calculateExampleSimilarity(candidate, selected) < 0.7); // Ngưỡng tương đồng

            if (isDiverseEnough) {
                diverseExamples.add(candidate);
            }
        }

        System.out.println("🔄 Áp dụng bộ lọc đa dạng: từ " + examples.size() + " ví dụ ban đầu còn " + diverseExamples.size() + " ví dụ.");
        return diverseExamples;
    }

    /**
     * Tính toán độ tương đồng giữa hai ví dụ (dựa trên Jaccard similarity)
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

    // Các hàm tiện ích (Helper methods)
    private boolean isStopWord(String word) {
        // Các từ dừng phổ biến trong tiếng Việt
        Set<String> stopWords = Set.of("là", "của", "và", "các", "có", "trong", "để", "thì", "khi", "ở", "tại", "cho");
        return stopWords.contains(word);
    }

    private String detectDomain(String query) {
        if (query.contains("chặn") || query.contains("deny") || query.contains("tấn công") || query.contains("bảo mật")) return "security";
        if (query.contains("lưu lượng") || query.contains("băng thông") || query.contains("mạng") || query.contains("network")) return "network";
        if (query.contains("user") || query.contains("người dùng") || query.contains("đăng nhập")) return "user_activity";
        if (query.contains("hiệu suất") || query.contains("chậm") || query.contains("nhanh")) return "performance";
        return "general";
    }

    private String detectIntent(String query) {
        if (query.contains("đếm") || query.contains("bao nhiêu") || query.contains("số lượng")) return "counting";
        if (query.contains("top") || query.contains("nhiều nhất") || query.contains("cao nhất")) return "ranking";
        if (query.contains("tìm") || query.contains("liệt kê") || query.contains("hiển thị")) return "search";
        if (query.contains("phân tích") || query.contains("so sánh") || query.contains("xu hướng")) return "analysis";
        return "general";
    }

    // Các lớp nội bộ để lưu trữ kết quả phân tích
    public static class QueryAnalysis {
        private final List<String> primaryKeywords;
        private final List<String> semanticKeywords;
        private final String domain;
        private final String intent;
        private final boolean hasTimeContext;

        public QueryAnalysis(List<String> primaryKeywords, List<String> semanticKeywords, String domain, String intent, boolean hasTimeContext) {
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
        private final double totalScore;

        public ExampleScore(DataExample example, double semanticScore, double contextScore, double domainScore, double totalScore) {
            this.example = example;
            this.semanticScore = semanticScore;
            this.contextScore = contextScore;
            this.domainScore = domainScore;
            this.totalScore = totalScore;
        }

        // Getters
        public DataExample getExample() { return example; }
        public double getTotalScore() { return totalScore; }
    }
}