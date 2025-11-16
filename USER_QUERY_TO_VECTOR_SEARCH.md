# 🔍 Từ User Query Đến Vector Search - Chi Tiết Quy Trình

## 📌 Tổng Quan

```
User Input: "Tôi muốn xem các lần đăng nhập thất bại"
   ↓
Convert to Vector (1536 numbers)
   ↓
Database Vector Search (PostgreSQL/Supabase với pgvector)
   ↓
Find top 10 most similar
   ↓
Return kết quả
```

---

## 🎯 Chi Tiết Quy Trình (6 Bước)

### Bước 1️⃣: User Gửi Query

**Controller (REST API):**
```
POST /api/chat-messages/compare/{sessionId}
{
  "message": "Tôi muốn xem các lần đăng nhập thất bại"
}
```

**File:** `src/main/java/com/example/chatlog/controller/ChatMessagesController.java`

```java
@PostMapping("/compare/{sessionId}")
public ResponseEntity<Map<String, Object>> sendMessageWithComparison(
    @PathVariable Long sessionId,
    @RequestBody ChatRequest chatRequest) {
    
    // chatRequest.message = "Tôi muốn xem các lần đăng nhập thất bại"
    
    Map<String, Object> comparisonResult = aiServiceImpl
        .handleRequestWithComparison(sessionId, chatRequest);
    
    return ResponseEntity.ok(comparisonResult);
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
@Transactional("secondaryTransactionManager")
public class VectorSearchService {
    
    @Autowired
    private EmbeddingModel embeddingModel;  // ← OpenAI embedding model
    
    @Autowired
    private AiEmbeddingService aiEmbeddingService;  // ← Database service
    
    public String findRelevantExamples(String userQuery) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🔍 VECTOR SEMANTIC SEARCH");
        System.out.println("=".repeat(100));
        
        // Check database stats first
        long totalEmbeddings = aiEmbeddingService.countAllNotDeleted();
        System.out.println("\n📊 DATABASE STATS:");
        System.out.println("   Total embeddings in database: " + totalEmbeddings);
        
        // ⭐ BƯỚC 3: Tạo Query Embedding
        // ... (xem chi tiết bên dưới)
    }
}
```

---

### Bước 4️⃣: Embedding Query & Database Search

**Chi tiết trong VectorSearchService:**

```java
// BƯỚC 4A: EMBEDDING QUERY
System.out.println("\n🔄 STEP 1: Creating Query Embedding for Semantic Search");
queryEmbedding = embeddingModel.embed(userQuery);

// Convert float[] to PostgreSQL format: "[0.1,0.2,...]"
StringBuilder sb = new StringBuilder("[");
for (int i = 0; i < queryEmbedding.length; i++) {
    if (i > 0) sb.append(",");
    sb.append(queryEmbedding[i]);
}
sb.append("]");
queryEmbeddingString = sb.toString();

// queryEmbeddingString = "[0.0234,-0.0156,0.0891,...]"  (1536 numbers)

// BƯỚC 4B: DATABASE VECTOR SEARCH
System.out.println("\n🎯 STEP 2: Vector Semantic Search");

List<AiEmbedding> similarEmbeddings = aiEmbeddingService
    .findSimilarEmbeddings(queryEmbeddingString, topK=10);

// SQL Query thực tế:
// SELECT * FROM ai_embedding 
// WHERE is_deleted = 0 
// ORDER BY embedding <=> CAST(:queryEmbedding AS vector) 
// LIMIT 10
```

**SQL Query Details:**
```sql
-- Cosine distance operator: <=>
-- Smaller value = more similar
-- ORDER BY ... LIMIT 10 = Top 10 most similar

SELECT * FROM ai_embedding 
WHERE is_deleted = 0 
ORDER BY embedding <=> CAST('[0.0234,-0.0156,0.0891,...]' AS vector) 
LIMIT 10;
```

**IVFFLAT Index:**
- Index được sử dụng tự động
- Fast approximate nearest neighbor search
- Performance: O(log n) thay vì O(n)

---

### Bước 5️⃣: Format Kết Quả

**Tiếp tục trong VectorSearchService:**

```java
// BƯỚC 5: FORMAT KẾT QUẢ
StringBuilder examples = new StringBuilder();
examples.append("RELEVANT EXAMPLES FROM KNOWLEDGE BASE\n");
examples.append("Mode: VECTOR\n\n");

for (int i = 0; i < similarEmbeddings.size(); i++) {
    AiEmbedding embedding = similarEmbeddings.get(i);
    
    examples.append("Example ").append(i + 1).append(":\n");
    
    // Extract từ metadata
    Object question = embedding.getMetadata().get("question");
    if (question != null) {
        examples.append("Question: ").append(question).append("\n");
    }
    
    String content = embedding.getContent();
    if (content != null && !content.isEmpty()) {
        String preview = content.length() > 180 ? content.substring(0, 180) + "..." : content;
        examples.append("Content: ").append(preview).append("\n");
    }
    
    Object scenario = embedding.getMetadata().get("scenario");
    if (scenario != null) {
        examples.append("Scenario: ").append(scenario).append("\n");
    }
    
    Object phase = embedding.getMetadata().get("phase");
    if (phase != null) {
        examples.append("Phase: ").append(phase).append("\n");
    }
    
    Object queryDsl = embedding.getMetadata().get("query_dsl");
    if (queryDsl != null) {
        examples.append("Query: ").append(queryDsl).append("\n\n");
    } else {
        examples.append("\n");
    }
}

return examples.toString();
```

**Output Format:**
```
RELEVANT EXAMPLES FROM KNOWLEDGE BASE
Mode: VECTOR

Example 1:
Question: Show failed authentication attempts
Content: Show failed authentication attempts...
Scenario: Authentication
Query: {"size": 100, "query": {"bool": {...}}}

Example 2:
Question: Display unsuccessful login events
Content: Display unsuccessful login events...
Scenario: Authentication
Query: {"size": 100, "query": {"bool": {...}}}

...
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
        dynamicExamples +  // ← Thêm top 10 similar examples
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

### Database Search Results

| # | Question | Similarity Score | Source |
|---|----------|------------------|--------|
| 1 | Show failed authentication attempts | **0.9871** ✅ | fortigate_queries_full.json |
| 2 | Display unsuccessful login events | **0.9854** ✅ | fortigate_queries_full.json |
| 3 | Get failed access attempts | **0.9821** ✅ | advanced_security_scenarios.json |
| 4 | List failed login attempts | **0.9798** ✅ | fortigate_queries_full.json |
| 5 | Show authentication failures | **0.9765** ✅ | fortigate_queries_full.json |
| ... | ... | ... | ... |

**Note:** Similarity score được tính bởi pgvector `<=>` operator (cosine distance). Smaller value = more similar.

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

**PostgreSQL pgvector:**
- Operator `<=>` tính cosine distance
- Distance = 1 - similarity
- Smaller distance = more similar

---

## ⏱️ Timeline

```
T+0ms:   User gửi query
T+50ms:  AiComparisonService nhận request
T+100ms: VectorSearchService.findRelevantExamples() gọi
T+150ms: embeddingModel.embed(userQuery) - Call OpenAI API
T+350ms: OpenAI trả về vector query (1536 dimensions)
T+360ms: Convert float[] to PostgreSQL format: "[0.1,0.2,...]"
T+370ms: Execute SQL query với pgvector <=> operator
T+420ms: Database trả về top 10 results (với IVFFLAT index)
T+430ms: Format results string
T+450ms: Return examples string
T+500ms: AiComparisonService thêm vào LLM prompt
T+600ms: Gửi prompt đến OpenAI/OpenRouter
T+3500ms: OpenAI trả về Elasticsearch query
T+3600ms: Return final response

Total: ~3.6 giây (phần lớn là chờ LLM)
Semantic Search: ~450ms (rất nhanh!)
```

---

## 🔄 Code Flow Diagram

```
ChatController
  └─ POST /api/chat-messages/compare/{sessionId}
     └─ ChatRequest: "Tôi muốn xem các lần đăng nhập thất bại"
        │
        └─ AiComparisonService.handleRequestWithComparison()
           │
           ├─ buildDynamicExamples(userQuery)
           │  │
           │  └─ VectorSearchService.findRelevantExamples()
           │     │
           │     ├─ Step 1: Embed query → Vector[1536]
           │     │  └─ embeddingModel.embed(userQuery)
           │     │     └─ OpenAI API: POST /v1/embeddings
           │     │
           │     ├─ Step 2: Convert to PostgreSQL format
           │     │  └─ "[0.1,0.2,...]"
           │     │
           │     ├─ Step 3: Database Vector Search
           │     │  └─ SQL: ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
           │     │     └─ IVFFLAT index used
           │     │     └─ Returns: List<AiEmbedding> (top 10)
           │     │
           │     └─ Step 4: Format results
           │        └─ Extract: question, query_dsl, scenario, phase
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
Convert to PostgreSQL format: "[0.1,0.2,...]"
   ↓
Database Vector Search (SQL với pgvector <=>)
   ↓
IVFFLAT index tối ưu search
   ↓
Top 10 kết quả tương đồng nhất
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

## 🔑 Key Points

1. **Database Storage**: Embeddings lưu trong PostgreSQL/Supabase, không phải file JSON
2. **Fast Search**: IVFFLAT index cho phép search nhanh O(log n)
3. **Top K Results**: Mặc định lấy 10 kết quả tốt nhất
4. **Similarity Score**: Tính bằng cosine distance (pgvector `<=>` operator)
5. **Optimized**: Chỉ search trong active records (`is_deleted = 0`)

---

---

## 📝 Tóm Tắt

```
User Query (tự nhiên)
   ↓
Embedding Model chuyển thành Vector 1536-chiều
   ↓
Convert to PostgreSQL format: "[0.1,0.2,...]"
   ↓
Database Vector Search (SQL với pgvector <=>)
   ↓
IVFFLAT index tối ưu search
   ↓
Top 10 kết quả tương đồng nhất
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

**Last Updated:** 2025-11-15  
**Version:** 2.0 (PostgreSQL/Supabase Implementation)
