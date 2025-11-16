# 🏗️ Vector Store Architecture - PostgreSQL/Supabase Implementation

## 📚 Mục Lục
1. [Khái Niệm Cơ Bản](#khái-niệm-cơ-bản)
2. [Quy Trình 5 Bước](#quy-trình-5-bước)
3. [Chi Tiết Kỹ Thuật](#chi-tiết-kỹ-thuật)
4. [Flow Thực Tế](#flow-thực-tế)
5. [Ví Dụ Code](#ví-dụ-code)

---

## 🎯 Khái Niệm Cơ Bản

### Vector là gì?

Vector là **một mảng số** đại diện cho **ý nghĩa** của một đoạn text.

```
Text: "Show failed authentication attempts"
                  ↓
        Embedding Model (AI)
                  ↓
Vector: [-0.234, 0.891, -0.456, 0.123, ... ] ← 1536 con số (OpenAI)
```

### Embedding Model

**Embedding Model** là một AI model chuyên việc:
- ✅ Nhận vào text (câu hỏi)
- ✅ Tách ra các ý nghĩa chính
- ✅ Chuyển thành vector (mảng số)
- ✅ Trả ra vector đó

**Ví dụ:**
- OpenAI text-embedding-3-small: 1536 dimensions (dự án này sử dụng)
- OpenAI text-embedding-3-large: 3072 dimensions
- Google PaLM Embedding: 768 dimensions

---

## 🚀 Quy Trình 5 Bước

### 📌 **Bước 1: Chuẩn Bị Dữ Liệu (Data Preparation)**

**Giai đoạn:** Ứng dụng chưa khởi động

```
┌─────────────────────────────────────┐
│      Tất cả file JSON trong        │
│     src/main/resources/            │
├─────────────────────────────────────┤
│ 📄 fortigate_queries_full.json      │ → 184+ câu hỏi
│ 📄 ... (các file khác)              │
└─────────────────────────────────────┘
```

**File JSON format:**
```json
[
  {
    "question": "Show failed authentication attempts",
    "query": {
      "size": 100,
      "query": {
        "bool": {
          "must": [
            { "match": { "action": "failed" } }
          ]
        }
      }
    }
  },
  ...
]
```

---

### 📌 **Bước 2: Khởi Động Ứng Dụng (Application Startup)**

**Giai đoạn:** Khi `java -jar app.jar` hoặc `mvn spring-boot:run`

```
1️⃣ JVM khởi động
   ↓
2️⃣ Spring Framework khởi động
   ↓
3️⃣ VectorStoreConfig bean được tạo
   ├─ Tạo EmbeddingModel từ OpenAI API
   ├─ Tạo SimpleVectorStore (in-memory cache)
   └─ Log: "Embeddings persisted in PostgreSQL/Supabase"
   ↓
4️⃣ KnowledgeBaseIndexingService.indexKnowledgeBase() triggered
   ├─ Check: countBySourceFile() vs file entries
   ├─ If fileCount == dbCount: Skip file ✅
   └─ If fileCount > dbCount: Process new entries only
```

---

### 📌 **Bước 3: Vector Hóa Dữ Liệu (Embedding Process)** ⭐ **QUAN TRỌNG**

**Giai đoạn:** Lần đầu ứng dụng chạy hoặc có entries mới

**Chi tiết:**

```
KnowledgeBaseIndexingService.indexKnowledgeBase() trigger
   ↓
for (String fileName : knowledgeBaseFiles) {
    ↓
    1️⃣ Đọc file JSON (VD: fortigate_queries_full.json)
       ↓
    2️⃣ Parse thành List<DataExample>
       ↓
    3️⃣ So sánh count: fileCount vs dbCount
       ├─ If fileCount == dbCount: Skip file ✅
       └─ If fileCount > dbCount: Process new entries
       ↓
    4️⃣ FOR EACH new DataExample:
       ├─ Check duplicate: existsByContent()
       ├─ Lấy: example.getQuestion()
       │   ↓ "Show failed authentication attempts"
       │
       ├─ Lấy: example.getQuery()
       │   ↓ {...elasticsearch query JSON...}
       │
       ├─ **GỌI Embedding Model** ← BƯỚC QUAN TRỌNG
       │   │
       │   ├─→ Gửi question lên OpenAI API
       │   │   Request: POST https://api.openai.com/v1/embeddings
       │   │   Body: { "input": "Show failed authentication attempts" }
       │   │
       │   ├─→ OpenAI xử lý
       │   │   - Phân tích ngữ nghĩa
       │   │   - Tạo vector 1536-chiều
       │   │
       │   └─→ Trả về vector
       │       Vector: [-0.234, 0.891, -0.456, ...]
       │
       └─ Lưu vào PostgreSQL/Supabase
           ├─ content: "Show failed authentication attempts"
           ├─ embedding: vector(1536) ← pgvector type
           └─ metadata (JSONB):
              ├─ question: "Show failed authentication attempts"
              ├─ query_dsl: {...elasticsearch query...}
              └─ source_file: "fortigate_queries_full.json"
}
```

**⏱️ Thời gian:**
- Mỗi question: ~100-200ms (phụ thuộc mạng)
- 184 questions: **~20-40 phút** (nếu có rate limiting)
- **Optimized:** Chỉ xử lý entries mới, không tái tạo

---

### 📌 **Bước 4: Lưu Trữ Vector (Storage/Persistence)**

**Giai đoạn:** Sau khi vector hóa xong

```
PostgreSQL/Supabase Database
   │
   └─ Table: ai_embedding
      ├─ id (UUID)
      ├─ content (TEXT)
      ├─ embedding (vector(1536)) ← pgvector extension
      ├─ metadata (JSONB)
      ├─ created_at (TIMESTAMP)
      ├─ updated_at (TIMESTAMP)
      └─ is_deleted (INTEGER) ← Soft delete: 0=active, 1=deleted
      
      Indexes:
      ├─ IVFFLAT index (vector similarity search)
      ├─ GIN index (metadata JSONB queries)
      └─ BTREE index (is_deleted)
```

**Lợi ích của PostgreSQL persistence:**
- ✅ Lần sau khởi động nhanh (1-2 giây - chỉ check count)
- ✅ Tiết kiệm API calls đến OpenAI (chỉ xử lý entries mới)
- ✅ Không bị mất dữ liệu khi restart
- ✅ Scalable (hỗ trợ hàng triệu records)
- ✅ Fast search với IVFFLAT index

---

### 📌 **Bước 5: Tìm Kiếm Ngữ Nghĩa (Similarity Search)** ⭐ **TRONG RUNTIME**

**Giai đoạn:** Khi user gửi query

```
User Query: "Show me login failures from last hour"
   ↓
AiComparisonService.handleRequestWithComparison()
   ├─ buildDynamicExamples(userQuery)
   │  ↓
   │  VectorSearchService.findRelevantExamples(userQuery)
   │  ↓
   │  ┌─── MAGIC HAPPENS HERE ───┐
   │  │ 1️⃣ Embedding Model vector hóa query
   │  │    Input: "Show me login failures from last hour"
   │  │    Output: [-0.230, 0.895, -0.455, ...]  ← 1536 số
   │  │
   │  │ 2️⃣ Convert to PostgreSQL format
   │  │    "[0.1,0.2,0.3,...]"
   │  │
   │  │ 3️⃣ Database Vector Search (SQL với pgvector)
   │  │    SELECT * FROM ai_embedding
   │  │    WHERE is_deleted = 0
   │  │    ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
   │  │    LIMIT 10
   │  │    
   │  │    pgvector operator: <=> (cosine distance)
   │  │    IVFFLAT index used for fast search
   │  │    
   │  │    Returns: List<AiEmbedding> (top 10)
   │  │
   │  │ 4️⃣ Format results
   │  │    Extract: question, query_dsl, scenario, phase
   │  └──────────────────────────┘
   │
   └─ Format kết quả
      ↓
"RELEVANT EXAMPLES FROM KNOWLEDGE BASE
Mode: VECTOR

Example 1:
Question: Show failed authentication attempts
Query: {...elasticsearch query...}

Example 2:
Question: Display unsuccessful login events
Query: {...elasticsearch query...}

..."
      ↓
   Thêm vào LLM Prompt
      ↓
   LLM (OpenAI/OpenRouter) tạo Elasticsearch query tốt hơn
      ↓
   Trả về cho user ✅
```

---

## 🔧 Chi Tiết Kỹ Thuật

### Cosine Similarity là gì?

**Công thức:**
```
similarity = (A · B) / (||A|| × ||B||)

Trong đó:
- A · B = tích vô hướng (dot product)
- ||A|| = độ dài vector A
- ||B|| = độ dài vector B

Kết quả: 0.0 (hoàn toàn khác) ↔ 1.0 (hoàn toàn giống)
```

**PostgreSQL pgvector:**
- Operator `<=>` tính cosine distance
- Distance = 1 - similarity
- Smaller distance = more similar

### PostgreSQL pgvector vs SimpleVectorStore

| Tính Năng | SimpleVectorStore | PostgreSQL/pgvector (Current) |
|-----------|-------------------|-------------------------------|
| **Storage** | In-memory | Database (persistent) |
| **Persistence** | File JSON | PostgreSQL table |
| **Scalability** | ❌ Hạn chế (< 1M) | ✅ Excellent (millions) |
| **Performance** | O(n) scan | O(log n) with IVFFLAT index |
| **Search** | similaritySearch() | SQL với `<=>` operator |
| **Soft Delete** | No | Yes (is_deleted) |
| **Optimization** | Re-index all | Only new entries |

**Dự án này sử dụng:** PostgreSQL/Supabase với pgvector extension

---

## 📊 Flow Thực Tế

### Timeline Lần Khởi Động Thứ 1 (Lâu)

```
T+0s      → App starts
T+1s      → Spring Framework loaded
T+2s      → VectorStoreConfig created
           └─ Log: "Embeddings persisted in PostgreSQL/Supabase"
T+3s      → KnowledgeBaseIndexingService.indexKnowledgeBase() triggered
T+4s      → Đọc fortigate_queries_full.json (184 questions)
T+5s      → Check count: fileCount (184) vs dbCount (0)
           └─ fileCount > dbCount → Process 184 entries
T+6s      → Bắt đầu vector hóa question 1
           → Call OpenAI API: embedding("Show failed auth attempts")
           → Wait 200ms
           → Nhận vector: [-0.234, ...]
           → Save to PostgreSQL/Supabase
T+6.2s    → Vector hóa question 2
           → ...
T+200s    → Vector hóa question 184
T+201s    → ✅ Xong! App ready to serve

           ⏱️ Tổng: ~3-4 phút (184 questions)
```

### Timeline Lần Khởi Động Thứ 2+ (Nhanh - Optimized)

```
T+0s      → App starts
T+1s      → Spring Framework loaded
T+2s      → VectorStoreConfig created
T+3s      → KnowledgeBaseIndexingService.indexKnowledgeBase() triggered
T+4s      → Đọc fortigate_queries_full.json (184 questions)
T+5s      → Check count: fileCount (184) vs dbCount (184)
           └─ fileCount == dbCount → Skip file ✅
T+6s      → ✅ Xong! App ready to serve

           ⏱️ Tổng: ~1-2 giây (chỉ check count, không xử lý)
```

**Optimization:** So sánh count trước khi xử lý, chỉ xử lý entries mới

### Timeline Request Từ User

```
T+0ms     → User gửi: "Show failed authentication attempts"
T+10ms    → AiComparisonService.handleRequestWithComparison() called
T+50ms    → buildDynamicExamples("Show failed auth attempts")
T+60ms    → VectorSearchService.findRelevantExamples() called
T+70ms    → Embedding Model vector hóa query
           → Call OpenAI API: embedding("Show failed auth attempts")
           → Wait 150ms
T+220ms   → Nhận vector: [-0.230, ...]
T+230ms   → Convert to PostgreSQL format: "[0.1,0.2,...]"
T+240ms   → SQL Query với pgvector `<=>` operator
           → IVFFLAT index used
           → Returns top 10 results (~50-100ms)
T+340ms   → Format kết quả string
T+350ms   → Trả về cho AiComparisonService
T+355ms   → Thêm vào LLM Prompt
T+360ms   → OpenAI (temperature=0.0) tạo query
T+3500ms  → OpenAI trả về Elasticsearch query
T+3510ms  → OpenRouter (temperature=0.5) tạo query (parallel)
T+6000ms  → OpenRouter trả về query
T+6100ms  → Tìm kiếm Elasticsearch với cả 2 query
T+6500ms  → AiResponseService tạo response
T+10000ms → Trả về cho user

           ⏱️ Tổng: ~10 giây (phần lớn là LLM wait time)
           🔍 Semantic Search: ~0.35 giây (rất nhanh!)
```

---

## 💻 Ví Dụ Code

### 1️⃣ **VectorStoreConfig.java** - Tạo Bean

```java
@Configuration
public class VectorStoreConfig {
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // SimpleVectorStore chỉ là in-memory cache
        // Embeddings chính lưu trong PostgreSQL/Supabase
        SimpleVectorStore vectorStore = SimpleVectorStore
            .builder(embeddingModel)
            .build();
        
        return vectorStore;
    }
}
```

**Chức năng:**
- Tạo SimpleVectorStore làm in-memory cache
- Embeddings chính lưu trong database, không phải file JSON

---

### 2️⃣ **KnowledgeBaseIndexingService.java** - Vector Hóa

```java
@Service
public class KnowledgeBaseIndexingService {
    @Autowired
    private AiEmbeddingService aiEmbeddingService;
    
    @PostConstruct
    @Transactional("secondaryTransactionManager")
    public void indexKnowledgeBase() {
        // 1. Đọc JSON files từ resources
        // 2. So sánh count: fileCount vs dbCount
        // 3. Chỉ xử lý entries mới
        // 4. Tạo embedding và lưu vào PostgreSQL/Supabase
        // 5. Add to SimpleVectorStore (cache)
    }
}
```

**Key Features:**
- ✅ Optimized: Chỉ xử lý entries mới (so sánh count)
- ✅ Database persistence: Lưu vào PostgreSQL/Supabase
- ✅ Soft delete support: `is_deleted = 0`
- ✅ Transaction: Sử dụng secondaryTransactionManager
- ✅ Duplicate check: `existsByContent()` trước khi lưu

---

### 3️⃣ **VectorSearchService.java** - Tìm Kiếm

```java
@Service
@Transactional("secondaryTransactionManager")
public class VectorSearchService {
    @Autowired
    private EmbeddingModel embeddingModel;
    
    @Autowired
    private AiEmbeddingService aiEmbeddingService;
    
    public String findRelevantExamples(String userQuery) {
        // 1. Embed user query
        float[] embedding = embeddingModel.embed(userQuery);
        String embeddingString = convertToPostgreSQLFormat(embedding);
        
        // 2. Database vector search
        List<AiEmbedding> results = aiEmbeddingService
            .findSimilarEmbeddings(embeddingString, topK=10);
        
        // 3. Format results for LLM
        return formatForLLM(results);
    }
}
```

**Key Features:**
- ✅ Database search: Sử dụng pgvector `<=>` operator
- ✅ Fast: IVFFLAT index tối ưu
- ✅ Top K: Lấy 10 kết quả tốt nhất
- ✅ Format: Extract question, query_dsl, scenario, phase

---

### 4️⃣ **AiEmbeddingRepository.java** - Database Queries

```java
@Repository
public interface AiEmbeddingRepository extends JpaRepository<AiEmbedding, UUID> {
    
    // Vector similarity search
    @Query(nativeQuery = true, value = 
        "SELECT * FROM ai_embedding " +
        "WHERE is_deleted = 0 " +
        "ORDER BY embedding <=> CAST(:queryEmbedding AS vector) " +
        "LIMIT :limit")
    List<AiEmbedding> findSimilarEmbeddings(
        @Param("queryEmbedding") String queryEmbedding, 
        @Param("limit") int limit
    );
    
    // Count by source file
    @Query(nativeQuery = true, value = 
        "SELECT COUNT(*) FROM ai_embedding " +
        "WHERE metadata->>'source_file' = ?1 AND is_deleted = 0")
    long countBySourceFile(String sourceFile);
    
    // Check existence
    @Query(nativeQuery = true, value = 
        "SELECT COUNT(*) > 0 FROM ai_embedding " +
        "WHERE content = ?1 AND is_deleted = 0")
    boolean existsByContent(String content);
}
```

**Key Points:**
- `<=>` operator: Cosine distance (pgvector)
- `CAST(... AS vector)`: Convert string to vector type
- `LIMIT`: Top K results
- `is_deleted = 0`: Chỉ lấy active records

---

## 🎯 Tóm Tắt

```
┌─────────────────────────────────────────────────────────────┐
│         VECTOR DATABASE TRANSFORMATION FLOW                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1️⃣ PREPARE DATA                                            │
│     JSON files (184+ Q&A)                                   │
│                                                             │
│  2️⃣ STARTUP (First time)                                   │
│     App starts → VectorStoreConfig created                 │
│                                                             │
│  3️⃣ EMBEDDING (First time)                                 │
│     KnowledgeBaseIndexingService.indexKnowledgeBase()      │
│     For each question: vectorize → save to PostgreSQL      │
│                                                             │
│  4️⃣ PERSISTENCE                                            │
│     Save to PostgreSQL/Supabase (ai_embedding table)       │
│                                                             │
│  5️⃣ RUNTIME (Every request)                                │
│     User query → vectorize → SQL search → result          │
│                                                             │
│  ✅ Ready! Fast semantic search in real-time              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Điều Gì Sẽ Xảy Ra Nếu...?

### ❓ Nếu mình thêm file JSON mới?

```
1. Thêm file vào src/main/resources/
2. Thêm tên file vào knowledgeBaseFiles array
3. Restart app
4. Tự động phát hiện entries mới và vector hóa
5. Lưu vào PostgreSQL/Supabase
```

### ❓ Nếu mình muốn thay embedding model?

```
application.yaml:
spring:
  ai:
    openai:
      embedding:
        options:
          model: text-embedding-3-large  # Thay đổi model
```

### ❓ Nếu muốn tái tạo embeddings?

```
Option 1: Xóa records trong database
DELETE FROM ai_embedding 
WHERE metadata->>'source_file' = 'fortigate_queries_full.json';

Option 2: Restart application
- Application tự động phát hiện entries mới
- Chỉ xử lý entries chưa có trong database
```

### ❓ Nếu mình muốn top 10 thay vì top 5?

```
VectorSearchService.java:
  
  int topK = 10;  // ← Thay đổi số này
  List<AiEmbedding> results = aiEmbeddingService
      .findSimilarEmbeddings(embeddingString, topK);
```

---

**Last Updated:** 2025-11-15  
**Version:** 3.0 (PostgreSQL/Supabase Implementation)
