# ⚡ Vector Database - Quick Reference Guide

## 🎯 1 Câu Giải Thích

**Vector Database** là một cơ sở dữ liệu chuyên lưu trữ "chữ ký ý nghĩa" của text, cho phép tìm kiếm ngữ nghĩa thay vì chỉ khớp từ khóa.

---

## 📊 Quy Trình Từ Text Đến Vector Database

```
BƯỚC 1: Text Input
┌────────────────────────────┐
│ "Show failed auth attempts"│  ← Câu hỏi của user
└────────────┬───────────────┘
             │
             ▼
BƯỚC 2: Embedding (Chuyển thành vector)
┌──────────────────────────────────────┐
│ OpenAI Embedding Model               │
│ nhận: "Show failed auth attempts"    │
│ output: 1536 con số                  │
│ [-0.234, 0.891, -0.456, 0.123, ...] │
└────────────┬─────────────────────────┘
             │
             ▼
BƯỚC 3: Vector Storage (Lưu trữ)
┌─────────────────────────────┐
│ Vector Database (In-Memory)  │
├─────────────────────────────┤
│ Doc 1: [0.234, 0.891, ...]  │
│ Doc 2: [0.245, 0.885, ...]  │
│ Doc 3: [0.100, 0.700, ...]  │
│ ...                         │
│ Doc 2300: [..., ..., ...]   │
└────────────┬────────────────┘
             │
             ▼
BƯỚC 4: Persistence (Lưu xuống file)
┌──────────────────────────┐
│ vector_store.json        │
│ (125 MB trên disk)       │
└──────────────────────────┘
```

---

## 🔍 Khi User Gửi Query

```
User Query: "Display unsuccessful login events"
         │
         ▼
┌─ Embedding ─────────────────┐
│ Chuyển query thành vector   │
│ [-0.230, 0.895, -0.455, ...] ← 1536 con số
└──────────┬──────────────────┘
           │
           ▼
┌─ Similarity Search ─────────────────────┐
│ So sánh vector query với 2300 vectors  │
│                                         │
│ Query vs Doc 1: 0.987 ← MATCH 98.7%    │
│ Query vs Doc 2: 0.985 ← MATCH 98.5%    │
│ Query vs Doc 3: 0.750 ← MATCH 75.0%    │
│ Query vs Doc 4: 0.250 ← MATCH 25.0%    │
│ ...                                     │
│ Lấy top 5 kết quả cao nhất            │
└──────────┬──────────────────┘
           │
           ▼
┌─ Return Top 5 Similar Documents ──┐
│ 1. "Show failed auth attempts"    │
│ 2. "Get failed access events"     │
│ 3. "Display failed logins"        │
│ 4. "Get unauthorized access"      │
│ 5. "Show auth errors"             │
└──────────┬───────────────────────┘
           │
           ▼
┌─ Add to LLM Prompt ────────────┐
│ "Dựa trên ví dụ tương đồng...  │
│  Hãy tạo Elasticsearch query"  │
└───────────────────────────────┘
```

---

## 🎬 Ví Dụ Thực Tế - Từng Bước

### Bước 1️⃣: Input JSON File

**File:** `fortigate_queries_full.json`

```json
[
  {
    "question": "Show failed authentication attempts",
    "query": {
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
  },
  {
    "question": "Display unsuccessful login events",
    "query": { ... }
  },
  ...
]
```

### Bước 2️⃣: Parse & Read

**Code:** `KnowledgeBaseIndexingService.indexKnowledgeBase()`

```java
// Đọc file
ClassPathResource resource = new ClassPathResource("fortigate_queries_full.json");
InputStream inputStream = resource.getInputStream();

// Parse thành DataExample
List<DataExample> examples = objectMapper.readValue(
    inputStream, 
    new TypeReference<List<DataExample>>() {}
);
// examples = [
//   {question: "Show failed auth attempts", query: {...}},
//   {question: "Display unsuccessful logins", query: {...}},
//   ...
// ]
```

### Bước 3️⃣: Convert to Document

```java
for (DataExample example : examples) {
    // Tạo Document từ question
    Document doc = new Document(
        example.getQuestion(),  // ← "Show failed authentication attempts"
        Map.of(
            "question", example.getQuestion(),
            "query_dsl", example.getQuery().toString(),
            "source_file", "fortigate_queries_full.json"
        )
    );
    documents.add(doc);
}
// documents = [
//   Document(content="Show failed auth attempts", metadata={...}),
//   Document(content="Display unsuccessful logins", metadata={...}),
//   ...
// ]
```

### Bước 4️⃣: Embedding (Vector hóa)

```java
// Đưa documents vào vector store
vectorStore.add(documents);

// SimpleVectorStore sẽ tự động:
// 1. Lặp qua từng document
// 2. Gọi EmbeddingModel.embed(document.content)
//
// ⭐ MAGIC HAPPENS HERE:
//    - Document 1: "Show failed auth attempts"
//      → Call OpenAI API
//      → Trả về vector: [-0.234, 0.891, -0.456, 0.123, ...]
//      ← 1536 con số!
//
//    - Document 2: "Display unsuccessful logins"
//      → Call OpenAI API
//      → Trả về vector: [-0.245, 0.885, -0.450, 0.115, ...]
//      ← Khác 1 chút (vì ý nghĩa gần giống)
//
//    - Document 3: "Get failed access events"
//      → Trả về vector: [-0.240, 0.890, -0.455, 0.120, ...]
//      ← Gần giống Document 1
//
//    - Document 4: "List authorized users"
//      → Trả về vector: [0.800, -0.200, 0.100, -0.500, ...]
//      ← Hoàn toàn khác!

// ⏱️ Thời gian: ~100-200ms per document
//             ~30-60 phút cho 2300 documents
```

### Bước 5️⃣: Lưu Trữ (Persistence)

```java
// Lưu tất cả vectors xuống file
((SimpleVectorStore) vectorStore).save(vectorStoreFile);

// vector_store.json structure:
// {
//   "documents": [
//     {
//       "content": "Show failed authentication attempts",
//       "embedding": [-0.234, 0.891, -0.456, 0.123, ...],  ← Lưu vector!
//       "metadata": {
//         "question": "Show failed authentication attempts",
//         "query_dsl": "{...}",
//         "source_file": "fortigate_queries_full.json"
//       }
//     },
//     {
//       "content": "Display unsuccessful login events",
//       "embedding": [-0.245, 0.885, -0.450, 0.115, ...],
//       "metadata": {...}
//     },
//     ...
//   ]
// }

// 📊 File size: ~125 MB
// 📝 Number of documents: ~2300
```

---

## 🔎 Bước 6️⃣: Tìm Kiếm (Similarity Search) ⭐

### Khi User Gửi Query

```
Input: "Show me failed login attempts"
       ↓
1️⃣ Vector hóa query
   VectorStore.similaritySearch("Show me failed login attempts")
   → Call EmbeddingModel.embed("Show me failed login attempts")
   → Trả về: [-0.232, 0.893, -0.454, 0.122, ...]  ← Query vector
   
2️⃣ So sánh độ tương đồng (Cosine Similarity)
   
   Query Vector:   [-0.232, 0.893, -0.454, ...]
   Doc 1 Vector:   [-0.234, 0.891, -0.456, ...]
   ↓
   Similarity = ((-0.232 × -0.234) + (0.893 × 0.891) + (-0.454 × -0.456) + ...)
                / (||Query|| × ||Doc1||)
   = 0.987  ← 98.7% match! ✅
   
   Doc 2 Vector:   [-0.245, 0.885, -0.450, ...]
   Similarity = 0.985  ← 98.5% match! ✅
   
   Doc 3 Vector:   [-0.100, 0.700, -0.300, ...]
   Similarity = 0.750  ← 75.0% match ✅
   
   Doc 4 Vector:   [0.800, -0.200, 0.100, ...]
   Similarity = 0.150  ← 15.0% match ❌

3️⃣ Sắp xếp và lấy top 5
   [0.987, 0.985, 0.750, 0.680, 0.670]
   ↑      ↑      ↑      ↑      ↑
   Top1   Top2   Top3   Top4   Top5

4️⃣ Trả về 5 documents tương đồng nhất
   Result = [
     Document(question="Show failed auth attempts", similarity=0.987),
     Document(question="Display unsuccessful logins", similarity=0.985),
     Document(question="Get failed access events", similarity=0.750),
     Document(question="Show unauthorized attempts", similarity=0.680),
     Document(question="Get login failures", similarity=0.670)
   ]
```

---

## ⏱️ Timeline

### Khởi Động Lần 1 (Lâu - Lần Đầu)
```
T+0s    App starts
T+1s    Spring Framework loads
T+2s    VectorStoreConfig created
        └─ Check: vector_store.json exists?
           └─ NO → Continue
T+3s    KnowledgeBaseIndexingService triggered
        └─ @PostConstruct starts
T+4s    Read file: fortigate_queries_full.json
T+10s   Parse 500 questions
T+12s   Start embedding (gọi OpenAI API 500 lần)
        └─ 200ms × 500 = 100 giây
T+112s  Vector hóa xong 500 questions
T+113s  Read file: advanced_security_scenarios.json
        └─ Tiếp tục vector hóa 200 questions
...
T+2400s Vector hóa xong 2300 questions
T+2410s Lưu file vector_store.json (125MB)
T+2420s ✅ APP READY!

⏰ Tổng: ~40 phút
```

### Khởi Động Lần 2+ (Nhanh)
```
T+0s    App starts
T+1s    Spring Framework loads
T+2s    VectorStoreConfig created
        └─ Check: vector_store.json exists?
           └─ YES! Load from file
T+1.5s  Parse JSON file (125MB)
        └─ Khôi phục 2300 vectors vào memory
T+2s    ✅ APP READY!

⏰ Tổng: ~2 giây
```

### Mỗi Request Từ User (Runtime)
```
T+0ms     User gửi: "Show failed auth attempts"
T+50ms    AiComparisonService.handleRequestWithComparison()
T+60ms    buildDynamicExamples(userQuery)
T+70ms    VectorSearchService.findRelevantExamples()
T+80ms    vectorStore.similaritySearch(query, topK=5)
T+100ms   Embedding query (Call OpenAI API)
T+250ms   Get embedding result: [-0.232, 0.893, -0.454, ...]
T+260ms   Calculate similarity with 2300 vectors (~100ms)
T+360ms   Sort and get top 5
T+370ms   Format result string
T+380ms   Return to AiComparisonService ✅

🔍 Semantic Search Duration: ~300ms (rất nhanh!)
```

---

## 🏗️ 4 Thành Phần Chính

### 1. VectorStoreConfig.java ⚙️
```java
@Configuration
public class VectorStoreConfig {
    private final File vectorStoreFile = new File("vector_store.json");
    
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // 1️⃣ Tạo SimpleVectorStore (in-memory)
        SimpleVectorStore vectorStore = SimpleVectorStore
            .builder(embeddingModel)
            .build();
        
        // 2️⃣ Nếu file tồn tại, load từ file
        if (vectorStoreFile.exists()) {
            System.out.println("✅ Tải Vector Store từ file");
            vectorStore.load(vectorStoreFile);
            // 2300 vectors được load vào memory
        } else {
            System.out.println("ℹ️ Sẽ tạo file mới sau khi indexing");
        }
        
        return vectorStore;  // ← Được inject vào các service khác
    }
}
```

**Khi nào chạy:** Khởi động app  
**Kết quả:** VectorStore bean được tạo

---

### 2. KnowledgeBaseIndexingService.java 📚
```java
@Service
public class KnowledgeBaseIndexingService {
    @Autowired
    private VectorStore vectorStore;
    
    private final File vectorStoreFile = new File("vector_store.json");
    
    @PostConstruct  // ← Chạy tự động khi app khởi động
    public void indexKnowledgeBase() {
        // 1️⃣ Skip nếu file đã tồn tại
        if (vectorStoreFile.exists()) {
            System.out.println("✅ Vector store đã tồn tại, skip indexing");
            return;  // ← RETURN! Không chạy nữa
        }
        
        System.out.println("🚀 Bắt đầu vector hóa kho tri thức...");
        
        String[] knowledgeBaseFiles = {
            "fortigate_queries_full.json",
            "advanced_security_scenarios.json",
            // ... (9 file khác)
        };
        
        List<Document> documents = new ArrayList<>();
        
        // 2️⃣ Đọc tất cả file JSON
        for (String fileName : knowledgeBaseFiles) {
            InputStream inputStream = resource.getInputStream();
            List<DataExample> examples = objectMapper.readValue(
                inputStream, 
                new TypeReference<List<DataExample>>() {}
            );
            
            // 3️⃣ Chuyển thành Document
            for (DataExample example : examples) {
                Document doc = new Document(
                    example.getQuestion(),  // ← "Show failed auth attempts"
                    Map.of(
                        "question", example.getQuestion(),
                        "query_dsl", example.getQuery().toString(),
                        "source_file", fileName
                    )
                );
                documents.add(doc);
            }
        }
        
        // 4️⃣ Vector hóa tất cả documents
        vectorStore.add(documents);
        // ⭐ SimpleVectorStore sẽ tự động gọi EmbeddingModel
        //    cho mỗi document
        
        // 5️⃣ Lưu xuống file
        ((SimpleVectorStore) vectorStore).save(vectorStoreFile);
        
        System.out.println("✅ Đã lưu " + documents.size() + " ví dụ");
    }
}
```

**Khi nào chạy:** @PostConstruct (khởi động app, nếu chưa có file)  
**Kết quả:** vector_store.json được tạo

---

### 3. VectorSearchService.java 🔍
```java
@Service
public class VectorSearchService {
    @Autowired
    private VectorStore vectorStore;
    
    public String findRelevantExamples(String userQuery) {
        System.out.println("🧠 Tìm kiếm: " + userQuery);
        
        // 1️⃣ Similarity search
        // ⭐ SimpleVectorStore sẽ:
        //    - Vector hóa userQuery bằng EmbeddingModel
        //    - Tính similarity với tất cả 2300 vectors
        //    - Return top 5 most similar documents
        List<Document> similarDocuments = vectorStore.similaritySearch(
            userQuery,  // ← "Show failed auth attempts"
            5           // ← Top K (có thể thay đổi)
        );
        
        if (similarDocuments.isEmpty()) {
            return "⚠️ Không tìm thấy ví dụ nào";
        }
        
        // 2️⃣ Format result
        StringBuilder result = new StringBuilder();
        result.append("RELEVANT EXAMPLES FROM KNOWLEDGE BASE:\n\n");
        
        for (int i = 0; i < similarDocuments.size(); i++) {
            Document doc = similarDocuments.get(i);
            result.append("Example ").append(i + 1).append(":\n");
            result.append("Question: ")
                .append(doc.getMetadata().get("question"))
                .append("\n");
            result.append("Query: ")
                .append(doc.getMetadata().get("query_dsl"))
                .append("\n\n");
        }
        
        System.out.println("✅ Tìm thấy " + similarDocuments.size() + " ví dụ");
        return result.toString();
    }
}
```

**Khi nào chạy:** Mỗi khi user gửi query  
**Kết quả:** Top 5 ví dụ tương đồng

---

### 4. AiComparisonService.java 🤖
```java
@Service
public class AiComparisonService {
    @Autowired
    private VectorSearchService vectorSearchService;
    
    public Map<String, Object> handleRequestWithComparison(
        Long sessionId, 
        ChatRequest chatRequest) {
        
        // ✅ Bước 1: Xây dựng dynamic examples từ vector search
        String dynamicExamples = buildDynamicExamples(
            chatRequest.message()  // ← "Show failed auth attempts"
        );
        
        // dynamicExamples sẽ chứa:
        // "RELEVANT EXAMPLES FROM KNOWLEDGE BASE:
        //  
        //  Example 1:
        //  Question: Show failed authentication attempts
        //  Query: {...elasticsearch query...}
        //  
        //  Example 2:
        //  Question: Display unsuccessful login events
        //  Query: {...elasticsearch query...}
        //  ..."
        
        // ✅ Bước 2: Thêm vào LLM prompt
        String combinedPrompt = systemPrompt 
            + "\n\nDYNAMIC EXAMPLES FROM KNOWLEDGE BASE\n" 
            + dynamicExamples;
        
        // ✅ Bước 3: OpenAI/OpenRouter tạo query dựa trên ví dụ
        String openaiQuery = chatClient
            .prompt(new Prompt(List.of(
                new SystemMessage(combinedPrompt),
                new UserMessage(chatRequest.message())
            )))
            .call()
            .content();
        
        // ... tiếp tục xử lý
        return result;
    }
    
    private String buildDynamicExamples(String userQuery) {
        // ⭐ GỌI VECTOR SEARCH
        return vectorSearchService.findRelevantExamples(userQuery);
    }
}
```

**Khi nào chạy:** Xử lý request so sánh  
**Kết quả:** Thêm ví dụ vào LLM prompt

---

## 🔢 Con Số Quan Trọng

| Thông Số | Giá Trị |
|----------|--------|
| **Số questions trong knowledge base** | 2300+ |
| **Embedding dimension (OpenAI)** | 1536 |
| **Vector store file size** | ~125 MB |
| **Khởi động lần 1** | 30-60 phút |
| **Khởi động lần 2+** | 1-2 giây |
| **Tìm kiếm per query** | 100-500 ms |
| **Top K results (mặc định)** | 5 |
| **Similarity score range** | 0.0 - 1.0 |

---

## 📝 Ví Dụ vector_store.json

```json
{
  "documents": [
    {
      "content": "Show failed authentication attempts",
      "embedding": [
        -0.234, 0.891, -0.456, 0.123, 0.045, ...
        // ← 1536 con số! (mỗi số đại diện cho một khía cạnh của ý nghĩa)
      ],
      "metadata": {
        "question": "Show failed authentication attempts",
        "query_dsl": "{\"size\": 100, \"query\": {\"bool\": {...}}}",
        "source_file": "fortigate_queries_full.json"
      }
    },
    {
      "content": "Display unsuccessful login events",
      "embedding": [
        -0.245, 0.885, -0.450, 0.115, 0.048, ...
        // ← Khác Doc 1 một chút (vì ý nghĩa gần giống)
      ],
      "metadata": { ... }
    },
    {
      "content": "Show users with failed logins",
      "embedding": [
        -0.240, 0.890, -0.455, 0.120, 0.046, ...
        // ← Rất giống Doc 1 (cùng ý nghĩa)
      ],
      "metadata": { ... }
    }
  ]
}
```

---

## 💡 Cosine Similarity - Cách Tính

**Công thức:**
```
similarity = (A · B) / (||A|| × ||B||)

Ý nghĩa:
- A · B = tích vô hướng (dot product)
- ||A|| = độ dài vector A
- ||B|| = độ dài vector B
- Kết quả từ 0.0 (hoàn toàn khác) đến 1.0 (giống 100%)
```

**Ví dụ:**
```
Query: "Show failed auth attempts"
Vector: [-0.232, 0.893, -0.454, ...]

Doc 1: "Show failed authentication attempts"
Vector: [-0.234, 0.891, -0.456, ...]
Similarity = 0.987  ✅ (98.7% match) ← TOP 1

Doc 2: "Display unsuccessful login events"
Vector: [-0.245, 0.885, -0.450, ...]
Similarity = 0.985  ✅ (98.5% match) ← TOP 2

Doc 3: "Show users with admin role"
Vector: [0.100, -0.500, 0.200, ...]
Similarity = 0.350  ❌ (35% match) ← NOT IN TOP 5

Doc 4: "Get failed access events"
Vector: [-0.240, 0.890, -0.455, ...]
Similarity = 0.982  ✅ (98.2% match) ← TOP 3
```

---

## 🚀 Bắt Đầu Sử Dụng

### Lần Đầu (Chờ ~ 40 phút)
```bash
mvn spring-boot:run

Console output:
ℹ️ Không tìm thấy file Vector Store, sẽ tạo file mới sau khi indexing.
🚀 Bắt đầu quá trình vector hóa kho tri thức...
(... đợi khoảng 30-60 phút ...)
✅ Đã vector hóa và lưu trữ 2300 ví dụ vào file vector_store.json
```

### Các Lần Sau (Nhanh ~ 2 giây)
```bash
mvn spring-boot:run

Console output:
✅ Tải Vector Store từ file: /path/to/vector_store.json
✅ Kho tri thức vector đã tồn tại. Bỏ qua bước indexing.
```

---

## ❓ FAQ

**Q: Tại sao lần đầu chậm?**  
A: Phải gọi OpenAI API 2300 lần để vector hóa mỗi question.

**Q: Tại sao lần 2+ nhanh?**  
A: Load từ file thay vì tính toán lại.

**Q: Vector là gì?**  
A: Mảng 1536 con số (từ OpenAI) đại diện ý nghĩa của text.

**Q: SimpleVectorStore có giới hạn không?**  
A: Tốt nhất cho < 1 triệu documents. Lớn hơn dùng Pinecone/Weaviate.

**Q: Thay đổi số top results như thế nào?**  
A: `vectorStore.similaritySearch(query, 10)` thay vì 5.

---

**Last Updated:** 2025-10-22  
**For more details:** See VECTOR_STORE_ARCHITECTURE.md
