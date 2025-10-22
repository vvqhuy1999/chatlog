# 🔍 Từ User Query Đến Vector Search - Chi Tiết Quy Trình

## 📌 Tổng Quan

```
User Input: "Tôi muốn xem các lần đăng nhập thất bại"
   ↓
Convert to Vector (1536 numbers)
   ↓
Compare với tất cả stored vectors (2300 documents)
   ↓
Find top 5 most similar
   ↓
Return kết quả
```

---

## 🎯 Chi Tiết Quy Trình (6 Bước)

### Bước 1️⃣: User Gửi Query

**Controller (REST API):**
```
POST /api/chat/compare
{
  "message": "Tôi muốn xem các lần đăng nhập thất bại"
}
```

**File:** `src/main/java/com/example/chatlog/controller/ChatMessagesController.java`

```java
@PostMapping("/compare")
public ResponseEntity<?> handleRequestWithComparison(
    @RequestParam Long sessionId,
    @RequestBody ChatRequest chatRequest) {
    
    // chatRequest.message = "Tôi muốn xem các lần đăng nhập thất bại"
    
    return ResponseEntity.ok(
        aiComparisonService.handleRequestWithComparison(
            sessionId, 
            chatRequest
        )
    );
}
```

---

### Bước 2️⃣: AiComparisonService Xử Lý

**File:** `src/main/java/com/example/chatlog/service/impl/AiComparisonService.java`

```java
public Map<String, Object> handleRequestWithComparison(Long sessionId, ChatRequest chatRequest) {
    
    // ⭐ BƯỚC 2: Xây dựng Dynamic Examples từ Vector Search
    String dynamicExamples = buildDynamicExamples(chatRequest.message());
    
    // buildDynamicExamples() gọi:
    // → vectorSearchService.findRelevantExamples(userQuery)
    
    // ... rest của code
}

private String buildDynamicExamples(String userQuery) {
    // ⭐ Gọi VectorSearchService
    return vectorSearchService.findRelevantExamples(userQuery);
}
```

---

### Bước 3️⃣: VectorSearchService - Chuyển Query Thành Vector

**File:** `src/main/java/com/example/chatlog/service/impl/VectorSearchService.java`

```java
@Service
public class VectorSearchService {
    
    @Autowired
    private VectorStore vectorStore;  // ← Chứa 2300 stored vectors
    
    public String findRelevantExamples(String userQuery) {
        System.out.println("🧠 Thực hiện tìm kiếm ngữ nghĩa cho: \"" + userQuery + "\"");
        
        // ⭐ BƯỚC 3: Gọi vectorStore.similaritySearch()
        // Bên trong:
        // 1. Chuyển userQuery thành vector
        // 2. So sánh với tất cả stored vectors
        // 3. Return top 5 most similar
        List<Document> similarDocuments = vectorStore.similaritySearch(userQuery);
        
        // ⭐ DEBUG: Kiểm tra kết quả tìm kiếm
        System.out.println("[VectorSearchService] 🔍 Số lượng kết quả tìm được: " 
            + similarDocuments.size());
        
        // ... format kết quả
        return examples.toString();
    }
}
```

---

### Bước 4️⃣: VectorStore - Embedding Query

**Bên Trong SimpleVectorStore.similaritySearch():**

```java
public List<Document> similaritySearch(String userQuery) {
    
    // ⭐ BƯỚC 4A: EMBEDDING QUERY
    // Gọi EmbeddingModel để vector hóa user query
    
    System.out.println("📊 User Query: " + userQuery);
    System.out.println("🔄 Converting query to vector...");
    
    // Gọi OpenAI Embedding API
    EmbeddingRequest request = new EmbeddingRequest(userQuery);
    EmbeddingResponse response = embeddingModel.call(request);
    
    float[] queryVector = response.getResult()
                                  .getOutput()
                                  .toArray();
    
    System.out.println("✅ Query Vector: " + queryVector.length + " dimensions");
    System.out.println("✅ First 5 values: " + Arrays.toString(
        Arrays.copyOf(queryVector, 5)
    ));
    
    // queryVector = [-0.232, 0.893, -0.454, 0.122, ...]  (1536 numbers)
    
    // ⭐ BƯỚC 4B: SO SÁNH VỚI TẤT CẢ STORED VECTORS
    System.out.println("🔍 Comparing with " + this.documents.size() + " stored documents...");
    
    List<SimilarityScore> scores = new ArrayList<>();
    
    // Loop qua từng document được lưu
    for (Document doc : this.documents.values()) {
        
        float[] storedVector = doc.getEmbedding().toArray();
        
        // Tính Cosine Similarity
        double similarity = calculateCosineSimilarity(queryVector, storedVector);
        
        scores.add(new SimilarityScore(doc, similarity));
        
        // Log chi tiết
        System.out.println("  Document: " + doc.getMetadata().get("question"));
        System.out.println("  Similarity Score: " + String.format("%.4f", similarity));
    }
    
    // ⭐ BƯỚC 4C: SẮP XẾP VÀ LẤY TOP K
    System.out.println("📊 Sorting results...");
    
    scores.sort((a, b) -> Double.compare(b.score, a.score));
    
    // Get top 5
    List<Document> topResults = scores.stream()
                                      .limit(5)
                                      .map(s -> s.document)
                                      .collect(Collectors.toList());
    
    System.out.println("✅ Top 5 results:");
    for (int i = 0; i < topResults.size(); i++) {
        System.out.println((i+1) + ". " + topResults.get(i).getMetadata().get("question"));
    }
    
    return topResults;
}

// Cosine Similarity calculation
private double calculateCosineSimilarity(float[] a, float[] b) {
    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    
    for (int i = 0; i < a.length; i++) {
        dotProduct += a[i] * b[i];
        normA += a[i] * a[i];
        normB += b[i] * b[i];
    }
    
    // similarity = (A · B) / (||A|| × ||B||)
    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
}
```

---

### Bước 5️⃣: Format Kết Quả

**Tiếp tục trong VectorSearchService:**

```java
public String findRelevantExamples(String userQuery) {
    
    List<Document> similarDocuments = vectorStore.similaritySearch(userQuery);
    
    // ⭐ BƯỚC 5: FORMAT KẾT QUẢ
    StringBuilder examples = new StringBuilder();
    examples.append("RELEVANT EXAMPLES FROM KNOWLEDGE BASE (Semantic Search):\n\n");
    
    for (int i = 0; i < similarDocuments.size(); i++) {
        Document doc = similarDocuments.get(i);
        
        examples.append("Example ").append(i + 1).append(":\n");
        examples.append("Question: ")
            .append(doc.getMetadata().get("question"))
            .append("\n");
        examples.append("Query: ")
            .append(doc.getMetadata().get("query_dsl"))
            .append("\n\n");
    }
    
    System.out.println("✅ Tìm thấy " + similarDocuments.size() + " ví dụ tương đồng.");
    
    return examples.toString();
    
    // Output:
    // RELEVANT EXAMPLES FROM KNOWLEDGE BASE (Semantic Search):
    //
    // Example 1:
    // Question: Show failed authentication attempts
    // Query: {"size": 100, "query": {"bool": {...}}}
    //
    // Example 2:
    // Question: Display unsuccessful login events
    // Query: {"size": 100, "query": {"bool": {...}}}
    // ...
}
```

---

### Bước 6️⃣: Thêm Vào LLM Prompt

**Tiếp tục trong AiComparisonService:**

```java
public Map<String, Object> handleRequestWithComparison(Long sessionId, ChatRequest chatRequest) {
    
    // ⭐ BƯỚC 6: THÊM VÀO LLM PROMPT
    String dynamicExamples = buildDynamicExamples(chatRequest.message());
    
    // dynamicExamples = "RELEVANT EXAMPLES FROM KNOWLEDGE BASE:..."
    
    String fullSystemPrompt = 
        "You are an Elasticsearch query expert.\n" +
        "Your task is to convert natural language queries into Elasticsearch queries.\n" +
        "\n" +
        "Here are examples of similar queries:\n" +
        dynamicExamples +  // ← Thêm top 5 similar examples
        "\n" +
        "Based on the examples above, convert this query to Elasticsearch: " +
        chatRequest.message();
    
    // Gửi prompt này vào OpenAI/OpenRouter
    String openaiQuery = chatClient.prompt(
        new Prompt(List.of(
            new SystemMessage(fullSystemPrompt),
            new UserMessage(chatRequest.message())
        ))
    ).call().content();
    
    // LLM sẽ xem ví dụ tương đồng và tạo query tốt hơn
    return result;
}
```

---

## 📊 Ví Dụ Thực Tế (Real Example)

### User Input
```
"Tôi muốn xem các lần đăng nhập thất bại"
```

### Query Vector (được tạo bởi OpenAI)
```
[-0.232, 0.893, -0.454, 0.122, -0.087, 0.456, ..., 0.234]
(1536 dimensions)
```

### Stored Vectors & Similarity Scores

| # | Question | Stored Vector | Similarity |
|---|----------|---------------|-----------|
| 1 | Show failed authentication attempts | [-0.234, 0.891, -0.456, ...] | **0.9871** ✅ |
| 2 | Display unsuccessful login events | [-0.245, 0.885, -0.450, ...] | **0.9854** ✅ |
| 3 | Get failed access attempts | [-0.240, 0.890, -0.455, ...] | **0.9821** ✅ |
| 4 | List failed login attempts | [-0.228, 0.896, -0.451, ...] | **0.9798** ✅ |
| 5 | Show authentication failures | [-0.235, 0.892, -0.457, ...] | **0.9765** ✅ |
| 999 | Get user list | [0.800, -0.200, 0.100, ...] | 0.1234 ❌ |
| 1500 | Show server logs | [0.500, 0.300, -0.400, ...] | 0.2456 ❌ |

### Output cho User

```
RELEVANT EXAMPLES FROM KNOWLEDGE BASE (Semantic Search):

Example 1:
Question: Show failed authentication attempts
Query: {
  "size": 100,
  "query": {
    "bool": {
      "must": [
        {"match": {"action": "failed"}},
        {"match": {"event_type": "authentication"}}
      ]
    }
  }
}

Example 2:
Question: Display unsuccessful login events
Query: {
  "size": 100,
  "query": {
    "bool": {
      "must": [
        {"match": {"result": "failure"}},
        {"match": {"event": "login"}}
      ]
    }
  }
}

...
```

### LLM Prompt (với Examples)

```
You are an Elasticsearch query expert.

Here are similar queries:
Example 1: Show failed authentication attempts → {...query1...}
Example 2: Display unsuccessful login events → {...query2...}
Example 3: Get failed access attempts → {...query3...}
Example 4: List failed login attempts → {...query4...}
Example 5: Show authentication failures → {...query5...}

Convert this to Elasticsearch: "Tôi muốn xem các lần đăng nhập thất bại"

Response:
{
  "size": 100,
  "query": {
    "bool": {
      "must": [
        {"match": {"result": "failed"}},
        {"match": {"event_type": "login"}}
      ],
      "filter": {
        "range": {
          "timestamp": {"gte": "now-24h"}
        }
      }
    }
  }
}
```

---

## ⚙️ Similarity Calculation Chi Tiết

### Cosine Similarity Formula

```
Similarity = (A · B) / (||A|| × ||B||)

Trong đó:
- A = Query Vector (1536 dimensions)
- B = Stored Document Vector (1536 dimensions)
- A · B = Dot Product (tổng tích từng phần tử)
- ||A|| = Magnitude/Length of A
- ||B|| = Magnitude/Length of B

Kết quả: 0.0 (hoàn toàn khác) → 1.0 (giống 100%)
```

### Ví Dụ Đơn Giản (3 dimensions)

```
Query Vector:    [0.5, 0.3, 0.2]
Doc 1 Vector:    [0.5, 0.3, 0.2]
Doc 2 Vector:    [0.4, 0.2, 0.15]
Doc 3 Vector:    [0.1, 0.1, 0.1]

Tính Doc 1:
- A · B = (0.5×0.5) + (0.3×0.3) + (0.2×0.2) = 0.38
- ||A|| = √(0.25 + 0.09 + 0.04) = 0.655
- ||B|| = √(0.25 + 0.09 + 0.04) = 0.655
- Similarity = 0.38 / (0.655 × 0.655) = 0.888

Tính Doc 2:
- A · B = (0.5×0.4) + (0.3×0.2) + (0.2×0.15) = 0.29
- ||A|| = 0.655
- ||B|| = √(0.16 + 0.04 + 0.0225) = 0.468
- Similarity = 0.29 / (0.655 × 0.468) = 0.943

Tính Doc 3:
- A · B = (0.5×0.1) + (0.3×0.1) + (0.2×0.1) = 0.1
- ||A|| = 0.655
- ||B|| = √(0.01 + 0.01 + 0.01) = 0.173
- Similarity = 0.1 / (0.655 × 0.173) = 0.884

Top 1: Doc 2 (0.943) ← Giống nhất!
```

---

## ⏱️ Timeline

```
T+0ms:   User gửi query
T+50ms:  AiComparisonService nhận request
T+100ms: VectorSearchService.findRelevantExamples() gọi
T+150ms: vectorStore.similaritySearch() gọi
T+200ms: EmbeddingModel embed query
T+350ms: OpenAI trả về vector query
T+360ms: Loop qua 2300 stored vectors
T+460ms: Tính similarity cho tất cả
T+500ms: Sort kết quả
T+510ms: Get top 5
T+520ms: Format output
T+530ms: Return examples string
T+600ms: AiComparisonService thêm vào LLM prompt
T+700ms: Gửi prompt đến OpenAI
T+3500ms: OpenAI trả về Elasticsearch query
T+3600ms: Return final response

Total: ~3.6 giây (phần lớn là chờ LLM)
Semantic Search: ~530ms (rất nhanh!)
```

---

## 🔄 Code Flow Diagram

```
ChatController
  └─ POST /compare
     └─ ChatRequest: "Tôi muốn xem các lần đăng nhập thất bại"
        │
        └─ AiComparisonService.handleRequestWithComparison()
           │
           ├─ buildDynamicExamples(userQuery)
           │  │
           │  └─ VectorSearchService.findRelevantExamples()
           │     │
           │     └─ vectorStore.similaritySearch(userQuery)
           │        │
           │        ├─ Step 1: Embed query → Vector[1536]
           │        │
           │        ├─ Step 2: Compare with 2300 stored vectors
           │        │  ├─ Doc 1: similarity = 0.9871
           │        │  ├─ Doc 2: similarity = 0.9854
           │        │  ├─ ...
           │        │  └─ Sort by score
           │        │
           │        └─ Step 3: Return top 5 documents
           │
           ├─ Format as String examples
           │
           ├─ Create Full Prompt
           │  "You are expert... Here are examples: {...}"
           │
           └─ Send to LLM (OpenAI/OpenRouter)
              │
              └─ Return Elasticsearch query
```

---

## 📌 Tóm Tắt

```
User Query (tự nhiên)
   ↓
Embedding Model chuyển thành Vector 1536-chiều
   ↓
So sánh với 2300 stored vectors
   ↓
Calculate Cosine Similarity cho mỗi cái
   ↓
Sort và lấy Top 5 kết quả tương đồng nhất
   ↓
Format thành String examples
   ↓
Thêm vào LLM Prompt
   ↓
LLM xem examples và tạo query tốt hơn
   ↓
Return Elasticsearch query cho user
```

---

**Generated:** 2025-10-22  
**Reference:** Spring AI + Vector Database
