# 🧠 Vector Store Guide - PostgreSQL/Supabase Implementation

## 📋 Tổng Quan

Hệ thống sử dụng **PostgreSQL/Supabase với pgvector extension** để lưu trữ và tìm kiếm embeddings. Điều này cho phép:
- ✅ Lưu trữ persistent trong database
- ✅ Tìm kiếm nhanh với IVFFLAT index
- ✅ Scalable và production-ready
- ✅ Soft delete support
- ✅ Optimized: Chỉ xử lý entries mới

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Vector Store System                  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Knowledge Base Files (JSON)                            │
│  └─ fortigate_queries_full.json                        │
│     └─ DataExample[] (question + query)                │
│         ↓                                               │
│  KnowledgeBaseIndexingService                           │
│  ├─ Read JSON files                                     │
│  ├─ Compare count: fileCount vs dbCount                │
│  ├─ Create embeddings (OpenAI API) - chỉ entries mới  │
│  └─ Save to PostgreSQL/Supabase                        │
│         ↓                                               │
│  PostgreSQL/Supabase Database                           │
│  └─ Table: ai_embedding                                 │
│     ├─ id (UUID)                                        │
│     ├─ content (TEXT)                                   │
│     ├─ embedding (vector(1536)) ← pgvector             │
│     ├─ metadata (JSONB)                                 │
│     └─ is_deleted (INTEGER)                             │
│         ↓                                               │
│  VectorSearchService                                    │
│  ├─ Embed user query                                    │
│  ├─ SQL: ORDER BY embedding <=> :queryVector            │
│  └─ Return top K results                               │
│         ↓                                               │
│  AiComparisonService                                    │
│  └─ Add examples to LLM prompt                          │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 📊 Database Schema

### Table: `ai_embedding`

```sql
CREATE TABLE ai_embedding (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,                    -- Câu hỏi gốc
    embedding vector(1536) NOT NULL,          -- Vector embedding (pgvector)
    metadata JSONB,                          -- {question, query_dsl, source_file, scenario, phase, ...}
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_deleted INTEGER DEFAULT 0             -- Soft delete: 0=active, 1=deleted
);

-- Indexes
CREATE INDEX idx_ai_embedding_vector 
    ON ai_embedding USING ivfflat (embedding vector_cosine_ops) 
    WITH (lists = '100');

CREATE INDEX idx_ai_embedding_metadata 
    ON ai_embedding USING gin (metadata);

CREATE INDEX idx_ai_embedding_is_deleted 
    ON ai_embedding (is_deleted);
```

**Key Points:**
- `embedding vector(1536)`: Sử dụng pgvector extension, 1536 dimensions (OpenAI text-embedding-3-small)
- `ivfflat` index: Tối ưu cho cosine similarity search
- `GIN` index: Tối ưu cho JSONB metadata queries
- Soft delete: `is_deleted = 0` cho active records

---

## 🚀 Setup Steps

### Bước 1: Database Setup (Supabase)

1. **Mở Supabase SQL Editor**
   - Visit: https://app.supabase.com
   - Go to SQL Editor

2. **Chạy script `sql/embedding.sql`**
   ```sql
   -- Enable pgvector extension
   CREATE EXTENSION IF NOT EXISTS vector;
   
   -- Create table
   CREATE TABLE ai_embedding (
       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       content TEXT NOT NULL,
       embedding vector(1536) NOT NULL,
       metadata JSONB,
       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
       is_deleted INTEGER DEFAULT 0
   );
   
   -- Create indexes
   CREATE INDEX idx_ai_embedding_vector 
       ON ai_embedding USING ivfflat (embedding vector_cosine_ops) 
       WITH (lists = '100');
   
   CREATE INDEX idx_ai_embedding_metadata 
       ON ai_embedding USING gin (metadata);
   
   CREATE INDEX idx_ai_embedding_is_deleted 
       ON ai_embedding (is_deleted);
   ```

3. **Verify table created**
   ```sql
   SELECT * FROM ai_embedding LIMIT 1;
   ```

---

### Bước 2: Application Configuration

**application.yaml:**
```yaml
spring:
  datasource:
    secondary:
      url: jdbc:postgresql://[supabase-host]:5432/postgres
      username: ${SECONDARY_DATASOURCE_USERNAME}
      password: ${SECONDARY_DATASOURCE_PASSWORD}
      driver-class-name: org.postgresql.Driver
```

**Environment Variables:**
```powershell
$env:SECONDARY_DATASOURCE_USERNAME = "postgres.wdxshprlefoixyyuxcwl"
$env:SECONDARY_DATASOURCE_PASSWORD = "your_password"
```

---

### Bước 3: First Run

```bash
mvn spring-boot:run
```

**Console Output:**
```
✅ Vector Store initialized (in-memory with Database persistence)
   Embeddings will be persisted in: PostgreSQL/Supabase Database

🚀 Bắt đầu quá trình vector hóa kho tri thức và lưu vào Database...
📁 File: fortigate_queries_full.json
   📊 Số entries trong file: 184
   💾 Số embeddings trong DB: 0
   🆕 Phát hiện 184 entries mới cần thêm vào DB
   ✅ Đã xử lý 184 entries mới từ file fortigate_queries_full.json

📊 === KẾT QUẢ TỔNG HỢP ===
✅ Đã thêm 184 embeddings mới vào Database
📊 Tổng số embeddings hiện tại trong DB: 184
🎉 Hoàn thành quá trình đồng bộ!
```

**Thời gian:** ~30-60 phút (tùy số lượng entries và API rate limit)

---

### Bước 4: Subsequent Runs (Optimized)

**Console Output:**
```
✅ Vector Store initialized (in-memory with Database persistence)

🚀 Bắt đầu quá trình vector hóa kho tri thức và lưu vào Database...
📁 File: fortigate_queries_full.json
   📊 Số entries trong file: 184
   💾 Số embeddings trong DB: 184
   ✅ Dữ liệu đã đồng bộ, bỏ qua file này

📊 === KẾT QUẢ TỔNG HỢP ===
✅ Đã thêm 0 embeddings mới vào Database
📊 Tổng số embeddings hiện tại trong DB: 184
🎉 Hoàn thành quá trình đồng bộ!
```

**Thời gian:** ~1-2 giây (chỉ check count, không xử lý)

**Optimization:**
- So sánh `fileCount` vs `dbCount` trước khi xử lý
- Chỉ xử lý entries mới (fileCount > dbCount)
- Không tái tạo embeddings đã có
- Fast startup nếu dữ liệu đã đồng bộ

---

## 🔄 Quy Trình Hoạt Động

### 1. Khởi Động Ứng Dụng (Startup)

```
Application Starts
    ↓
VectorStoreConfig.vectorStore()
    ├─ Tạo SimpleVectorStore (in-memory cache)
    └─ Log: "Embeddings persisted in PostgreSQL/Supabase"
    ↓
KnowledgeBaseIndexingService.indexKnowledgeBase()
    ├─ For each JSON file:
    │   ├─ Check: countBySourceFile() vs file entries
    │   ├─ If fileCount == dbCount: Skip file ✅
    │   ├─ If fileCount > dbCount: Process new entries only
    │   └─ For each new entry:
    │       ├─ Check duplicate: existsByContent()
    │       ├─ Create embedding (OpenAI API)
    │       ├─ Save to PostgreSQL/Supabase
    │       └─ Add to SimpleVectorStore (cache)
    ↓
Ready to serve ✅
```

---

### 2. Vector Search (Runtime)

```
User Query: "Show failed authentication attempts"
    ↓
VectorSearchService.findRelevantExamples(userQuery)
    ↓
STEP 1: Create Query Embedding
    ├─ embeddingModel.embed(userQuery)
    ├─ Convert float[] to PostgreSQL format: "[0.1,0.2,...]"
    └─ queryEmbeddingString = "[...]"
    ↓
STEP 2: Database Vector Search
    ├─ SQL Query:
    │   SELECT * FROM ai_embedding 
    │   WHERE is_deleted = 0 
    │   ORDER BY embedding <=> CAST(:queryEmbedding AS vector) 
    │   LIMIT 10
    │
    ├─ pgvector operator: <=> (cosine distance)
    ├─ IVFFLAT index used for fast search
    └─ Returns: List<AiEmbedding>
    ↓
STEP 3: Format Results
    ├─ Extract: question, query_dsl, scenario, phase
    └─ Format as String for LLM prompt
    ↓
Return: "RELEVANT EXAMPLES FROM KNOWLEDGE BASE..."
```

**SQL Query Details:**
```sql
-- Cosine distance operator: <=>
-- Smaller value = more similar
-- ORDER BY ... LIMIT 10 = Top 10 most similar

SELECT * FROM ai_embedding 
WHERE is_deleted = 0 
ORDER BY embedding <=> CAST('[0.1,0.2,...]' AS vector) 
LIMIT 10;
```

---

## 📁 Cấu Trúc Code

### 1. VectorStoreConfig.java

**File:** `src/main/java/com/example/chatlog/config/VectorStoreConfig.java`

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

### 2. KnowledgeBaseIndexingService.java

**File:** `src/main/java/com/example/chatlog/service/impl/KnowledgeBaseIndexingService.java`

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
        // 4. Tạo embedding và lưu vào database
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

### 3. VectorSearchService.java

**File:** `src/main/java/com/example/chatlog/service/impl/VectorSearchService.java`

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

### 4. AiEmbeddingRepository.java

**File:** `src/main/java/com/example/chatlog/repository/AiEmbeddingRepository.java`

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

## 🔍 Vector Search Chi Tiết

### PostgreSQL pgvector Operators

| Operator | Description | Use Case |
|----------|-------------|----------|
| `<=>` | Cosine distance | Similarity search (smaller = more similar) |
| `<->` | L2 distance | Euclidean distance |
| `<#>` | Negative inner product | Alternative similarity |

**Example:**
```sql
-- Cosine similarity (used in project)
ORDER BY embedding <=> CAST('[0.1,0.2,...]' AS vector)

-- Result: Smaller distance = more similar
-- 0.0 = identical, 1.0 = completely different
```

---

### IVFFLAT Index

```sql
CREATE INDEX idx_ai_embedding_vector
    ON ai_embedding USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = '100');
```

**How it works:**
1. Divides vector space into clusters (lists)
2. Searches within relevant clusters first
3. Much faster than full scan for large datasets

**Performance:**
- Without index: O(n) - scan all vectors
- With IVFFLAT: O(log n) - approximate nearest neighbor

---

## 📊 Performance Metrics

| Operation | Time | Notes |
|-----------|------|-------|
| **Startup (first time)** | 30-60 min | Vector hóa tất cả entries |
| **Startup (optimized)** | 1-2 sec | Chỉ check count, không xử lý |
| **Vector search** | 50-200ms | Với IVFFLAT index |
| **Embedding creation** | 100-200ms | OpenAI API call |

---

## 🔧 Configuration Options

### Thay Đổi Số Lượng Top Results

**File:** `VectorSearchService.java`
```java
int topK = 10; // ← Thay đổi số này
```

### Thêm Knowledge Base Files

**File:** `KnowledgeBaseIndexingService.java`
```java
String[] knowledgeBaseFiles = {
    "fortigate_queries_full.json",
    "my_new_file.json"  // ← Thêm file mới
};
```

---

## 🔄 Tái Tạo Embeddings

Nếu muốn tái tạo embeddings (sau khi thêm file JSON mới):

**Option 1: Xóa records trong database**
```sql
-- Xóa tất cả embeddings của một file
DELETE FROM ai_embedding 
WHERE metadata->>'source_file' = 'fortigate_queries_full.json';
```

**Option 2: Restart application**
- Application tự động phát hiện entries mới
- Chỉ xử lý entries chưa có trong database

---

## 🐛 Troubleshooting

### ❌ Lỗi: "vector type does not exist"

**Nguyên nhân:** Chưa enable pgvector extension  
**Giải pháp:** Chạy `CREATE EXTENSION IF NOT EXISTS vector;` trên Supabase

### ❌ Lỗi: "Cannot connect to Supabase"

**Nguyên nhân:** Sai credentials hoặc network issue  
**Giải pháp:** 
- Kiểm tra `SECONDARY_DATASOURCE_USERNAME` và `PASSWORD`
- Verify SSL connection

### ❌ Lỗi: "No embeddings found in database"

**Nguyên nhân:** Chưa chạy indexing process  
**Giải pháp:** Restart application để trigger `@PostConstruct`

### ❌ Lỗi: "IVFFLAT index not used"

**Nguyên nhân:** Index chưa được tạo  
**Giải pháp:** Chạy lại `CREATE INDEX` statement từ `embedding.sql`

---

## ✅ Kiểm Tra Kết Quả

### 1. Check Database

```sql
-- Count total embeddings
SELECT COUNT(*) FROM ai_embedding WHERE is_deleted = 0;

-- Check by source file
SELECT COUNT(*) FROM ai_embedding 
WHERE metadata->>'source_file' = 'fortigate_queries_full.json' 
AND is_deleted = 0;
```

### 2. Check Console Logs

```
📊 DATABASE STATS:
   Total embeddings in database: 184

🔍 VECTOR SEMANTIC SEARCH
   ✅ Found: 10 similar embeddings
```

### 3. Test Vector Search

Gửi query và kiểm tra logs:
```
User Query: "Show failed authentication attempts"
   ✅ Query Embedding Created: 1536 dimensions
   ✅ Found: 10 similar embeddings
```

---

## 📝 Key Differences from File-Based Approach

| Aspect | File JSON (Old) | PostgreSQL (Current) |
|--------|----------------|----------------------|
| **Storage** | vector_store.json | ai_embedding table |
| **Search** | SimpleVectorStore.similaritySearch() | SQL with pgvector `<=>` |
| **Persistence** | File on disk | Database |
| **Scalability** | Limited (< 1M docs) | Excellent (millions) |
| **Performance** | O(n) scan | O(log n) with index |
| **Soft Delete** | No | Yes (is_deleted) |
| **Optimization** | Re-index all | Only new entries |
| **Startup Time** | 1-2 sec (load file) | 1-2 sec (check count) |

---

## 🎯 Tóm Tắt

**Dự án sử dụng:**
- ✅ PostgreSQL/Supabase với pgvector extension
- ✅ Table `ai_embedding` với `vector(1536)` type
- ✅ IVFFLAT index cho fast similarity search
- ✅ Soft delete với `is_deleted` column
- ✅ Optimized indexing: Chỉ xử lý entries mới

**SimpleVectorStore:**
- Chỉ là in-memory cache
- Không phải storage chính
- Embeddings thực tế lưu trong database

**Vector Search:**
- SQL query với pgvector `<=>` operator
- Fast với IVFFLAT index
- Top K results (default: 10)

---

**Last Updated:** 2025-11-15  
**Version:** 2.0 (PostgreSQL/Supabase Implementation)
