# 🏗️ Vector Store Architecture - Chi Tiết Quá Trình Chuyển Thành Vector Database

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
- OpenAI text-embedding-3-small: 1536 dimensions
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
│ 📄 fortigate_queries_full.json      │ → 500+ câu hỏi
│ 📄 advanced_security_scenarios.json │ → 200+ câu hỏi
│ 📄 network_forensics_performance.json
│ 📄 ... (8 file khác)               │
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
  {
    "question": "Display unsuccessful login events",
    "query": { ... }
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
   ├─ Tạo SimpleVectorStore (bộ nhớ)
   └─ Kiểm tra: vector_store.json có tồn tại?
      │
      ├─→ CÓ: Load dữ liệu từ file
      │       ↓
      │       Xong! (⚡ 1-2 giây)
      │
      └─→ KHÔNG: Tiếp tục sang Bước 3
```

---

### 📌 **Bước 3: Vector Hóa Dữ Liệu (Embedding Process)** ⭐ **QUAN TRỌNG**

**Giai đoạn:** Lần đầu ứng dụng chạy, nếu chưa có `vector_store.json`

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
    3️⃣ FOR EACH DataExample:
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
       └─ Tạo Document object
           ├─ content: "Show failed authentication attempts"
           ├─ embedding: [-0.234, 0.891, -0.456, ...]
           └─ metadata:
              ├─ question: "Show failed authentication attempts"
              ├─ query_dsl: {...elasticsearch query...}
              └─ source_file: "fortigate_queries_full.json"
}
```

**⏱️ Thời gian:**
- Mỗi question: ~100-200ms (phụ thuộc mạng)
- 2300 questions: **~30-60 phút** (nếu có rate limiting)

---

### 📌 **Bước 4: Lưu Trữ Vector (Storage/Persistence)**

**Giai đoạn:** Sau khi vector hóa xong

```
SimpleVectorStore (in-memory)
   │
   ├─ Document 1: {content, embedding, metadata}
   ├─ Document 2: {content, embedding, metadata}
   ├─ Document 3: {content, embedding, metadata}
   └─ ... (2300+ documents)
   
   ↓ vectorStore.save(vectorStoreFile)
   
vector_store.json (file trên disk)
   
   JSON structure:
   {
     "documents": [
       {
         "content": "Show failed authentication attempts",
         "embedding": [-0.234, 0.891, -0.456, ...],  ← 1536 số!
         "metadata": {
           "question": "Show failed authentication attempts",
           "query_dsl": "{...}",
           "source_file": "fortigate_queries_full.json"
         }
       },
       {
         "content": "Display unsuccessful login events",
         "embedding": [-0.245, 0.885, -0.450, ...],  ← Khác 1 chút
         "metadata": {...}
       },
       ...
     ]
   }
   
   📊 File size: ~50-200 MB (tùy số documents)
```

**Lợi ích của persistence:**
- ✅ Lần sau khởi động nhanh (1-2 giây thay vì 30-60 phút)
- ✅ Tiết kiệm API calls đến OpenAI
- ✅ Không bị mất dữ liệu khi restart

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
   │  vectorStore.similaritySearch(userQuery, topK=5)
   │  ↓
   │  ┌─── MAGIC HAPPENS HERE ───┐
   │  │ 1️⃣ Embedding Model vector hóa query
   │  │    Input: "Show me login failures from last hour"
   │  │    Output: [-0.230, 0.895, -0.455, ...]  ← 1536 số
   │  │
   │  │ 2️⃣ Tính độ tương đồng (similarity) với tất cả documents
   │  │    Công thức: Cosine Similarity
   │  │    
   │  │    Query Vector:      [-0.230, 0.895, -0.455, ...]
   │  │    Doc 1 Vector:      [-0.234, 0.891, -0.456, ...]
   │  │    Similarity Score:  0.987 ← Rất giống! (0.0 - 1.0)
   │  │
   │  │    Doc 2 Vector:      [-0.245, 0.885, -0.450, ...]
   │  │    Similarity Score:  0.985 ← Rất giống!
   │  │
   │  │    Doc 3 Vector:      [-0.100, 0.700, -0.300, ...]
   │  │    Similarity Score:  0.750 ← Khá giống
   │  │
   │  │    Doc 4 Vector:      [0.500, -0.200, 0.800, ...]
   │  │    Similarity Score:  0.120 ← Không giống
   │  │
   │  │ 3️⃣ Sort theo similarity score và lấy topK=5
   │  │    Top 1: Doc 1 (0.987) ← "Show failed authentication attempts"
   │  │    Top 2: Doc 2 (0.985) ← "Display unsuccessful login events"
   │  │    Top 3: Doc 3 (0.750) ← "Get failed auth in last hour"
   │  │    Top 4: ...
   │  │    Top 5: ...
   │  └──────────────────────────┘
   │
   └─ Format kết quả
      ↓
"RELEVANT EXAMPLES FROM KNOWLEDGE BASE (Semantic Search):

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

**Ví dụ thực tế:**

```
Query vector:    [0.5, 0.3, 0.2]
Doc 1 vector:    [0.5, 0.3, 0.2]  ← Giống y hệt
Similarity:      1.0 ✅ (100% match)

Doc 2 vector:    [0.4, 0.2, 0.15] ← Khác chút
Similarity:      0.98 ✅ (98% match)

Doc 3 vector:    [0.1, 0.1, 0.1]  ← Khác hơn
Similarity:      0.89 ✅ (89% match)

Doc 4 vector:    [-0.5, -0.3, -0.2] ← Ngược hướng
Similarity:      -1.0 ❌ (0% match)
```

### SimpleVectorStore vs Production Vector DB

| Tính Năng | SimpleVectorStore | Pinecone | Weaviate | Milvus |
|-----------|-------------------|----------|----------|--------|
| **In-memory** | ✅ Đơn giản | ❌ Cloud | ❌ Distributed | ❌ Distributed |
| **Persistence** | ✅ File JSON | ✅ Cloud | ✅ Disk | ✅ Disk |
| **Scalability** | ❌ Hạn chế | ✅ Tuyệt vời | ✅ Tuyệt vời | ✅ Tuyệt vời |
| **Performance** | ⚡ Nhanh (< 100 examples) | ⚡⚡ Nhanh (millions) | ⚡⚡ Nhanh | ⚡⚡ Nhanh |
| **Setup** | ✅ Dễ (không cần) | ❌ Phức tạp | ❌ Phức tạp | ❌ Phức tạp |
| **Cost** | ✅ Free | ❌ $ | ❌ $ | ✅ Free |

---

## 📊 Flow Thực Tế

### Timeline Lần Khởi Động Thứ 1 (Lâu)

```
T+0s      → App starts
T+1s      → Spring Framework loaded
T+2s      → VectorStoreConfig created
T+3s      → KnowledgeBaseIndexingService.indexKnowledgeBase() triggered
T+4s      → Đọc fortigate_queries_full.json (500 questions)
T+5s      → Bắt đầu vector hóa question 1
           → Call OpenAI API: embedding("Show failed auth attempts")
           → Wait 200ms
           → Nhận vector: [-0.234, ...]
           → Tạo Document 1
T+5.2s    → Vector hóa question 2
           → ...
T+200s    → Vector hóa question 500
T+205s    → Đọc advanced_security_scenarios.json (200 questions)
T+405s    → Vector hóa tất cả 200 questions
T+410s    → ... (tiếp tục 9 file khác)
T+2400s   → Hoàn thành vector hóa 2300 questions
T+2401s   → vectorStore.save(vectorStoreFile)
T+2420s   → Write vector_store.json (125MB) to disk
T+2425s   → ✅ Xong! App ready to serve
           
           ⏱️ Tổng: ~40 phút (tùy mạng, API rate limit)
```

### Timeline Lần Khởi Động Thứ 2+ (Nhanh)

```
T+0s      → App starts
T+1s      → Spring Framework loaded
T+2s      → VectorStoreConfig created
T+3s      → Kiểm tra: vector_store.json tồn tại?
           → YES! 
           → Load from disk
T+1.5s    → Parse JSON file (125MB)
           → Khôi phục 2300 documents vào memory
T+2s      → ✅ Xong! App ready to serve
           
           ⏱️ Tổng: ~2 giây
```

### Timeline Request Từ User

```
T+0ms     → User gửi: "Show failed authentication attempts"
T+10ms    → AiComparisonService.handleRequestWithComparison() called
T+50ms    → buildDynamicExamples("Show failed auth attempts")
T+60ms    → VectorSearchService.findRelevantExamples() called
T+70ms    → vectorStore.similaritySearch(userQuery, topK=5)
T+80ms    → Embedding Model vector hóa query
           → Call OpenAI API: embedding("Show failed auth attempts")
           → Wait 150ms
T+230ms   → Nhận vector: [-0.230, ...]
T+240ms   → Tính similarity với 2300 documents
           → Tính xong trong ~100ms
T+340ms   → Sort và lấy top 5
T+350ms   → Format kết quả string
T+360ms   → Trả về cho AiComparisonService
T+365ms   → Thêm vào LLM Prompt
T+370ms   → OpenAI (temperature=0.0) tạo query
T+3500ms  → OpenAI trả về Elasticsearch query
T+3510ms  → OpenRouter (temperature=0.5) tạo query (parallel)
T+6000ms  → OpenRouter trả về query
T+6100ms  → Tìm kiếm Elasticsearch với cả 2 query
T+6500ms  → AiResponseService tạo response
T+10000ms → Trả về cho user
           
           ⏱️ Tổng: ~10 giây (phần lớn là LLM wait time)
           🔍 Semantic Search: ~0.3 giây (rất nhanh!)
```

---

## 💻 Ví Dụ Code

### 1️⃣ **VectorStoreConfig.java** - Tạo Bean

```java
@Configuration
public class VectorStoreConfig {
    private final File vectorStoreFile = new File("vector_store.json");
    
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // 1️⃣ Tạo SimpleVectorStore với EmbeddingModel
        SimpleVectorStore vectorStore = SimpleVectorStore
            .builder(embeddingModel)
            .build();
        
        // 2️⃣ Nếu file đã tồn tại, tải dữ liệu từ file
        if (vectorStoreFile.exists()) {
            System.out.println("✅ Tải Vector Store từ file: " 
                + vectorStoreFile.getAbsolutePath());
            vectorStore.load(vectorStoreFile);
            // Dữ liệu được load vào bộ nhớ
            // Sau đó SẴN SÀNG để tìm kiếm
        } else {
            System.out.println("ℹ️ Sẽ tạo file mới sau khi indexing");
        }
        
        return vectorStore;
    }
}
```

### 2️⃣ **KnowledgeBaseIndexingService.java** - Vector Hóa

```java
@Service
public class KnowledgeBaseIndexingService {
    @Autowired
    private VectorStore vectorStore; // ← Được inject từ config
    
    private final File vectorStoreFile = new File("vector_store.json");
    
    @PostConstruct  // ← Chạy tự động khi class được khởi tạo
    public void indexKnowledgeBase() {
        // CHỈ CHẠY NẾU CHƯA CÓ FILE
        if (vectorStoreFile.exists()) {
            System.out.println("✅ Vector store đã tồn tại, skip indexing");
            return;
        }
        
        System.out.println("🚀 Bắt đầu vector hóa...");
        
        String[] knowledgeBaseFiles = {
            "fortigate_queries_full.json",
            "advanced_security_scenarios.json",
            // ... (9 file khác)
        };
        
        ObjectMapper objectMapper = new ObjectMapper();
        List<Document> documents = new ArrayList<>();
        
        // 1️⃣ ĐỌC TẤT CẢ FILE JSON
        for (String fileName : knowledgeBaseFiles) {
            try {
                ClassPathResource resource = new ClassPathResource(fileName);
                InputStream inputStream = resource.getInputStream();
                List<DataExample> examples = objectMapper.readValue(
                    inputStream, 
                    new TypeReference<List<DataExample>>() {}
                );
                
                // 2️⃣ FOR EACH EXAMPLE
                for (DataExample example : examples) {
                    if (example.getQuestion() != null 
                        && example.getQuery() != null) {
                        
                        // 3️⃣ CHUYỂN THÀNH DOCUMENT
                        Document doc = new Document(
                            example.getQuestion(),  // ← Content
                            Map.of(
                                "question", example.getQuestion(),
                                "query_dsl", example.getQuery().toString(),
                                "source_file", fileName
                            )
                        );
                        documents.add(doc);
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi: " + e.getMessage());
            }
        }
        
        // 3️⃣ ĐƯA DOCUMENTS VÀO VECTOR STORE
        // ⭐ TẠO VECTOR TƯƠNG ỨNG CHO MỖI DOCUMENT
        vectorStore.add(documents);
        // SimpleVectorStore sẽ tự động:
        // - Gọi EmbeddingModel để vector hóa
        // - Lưu embedding vào bộ nhớ
        
        // 4️⃣ LƯU XUỐNG FILE
        if (vectorStore instanceof SimpleVectorStore) {
            ((SimpleVectorStore) vectorStore).save(vectorStoreFile);
        }
        
        System.out.println("✅ Đã lưu " + documents.size() 
            + " ví dụ vào " + vectorStoreFile);
    }
}
```

### 3️⃣ **VectorSearchService.java** - Tìm Kiếm

```java
@Service
public class VectorSearchService {
    @Autowired
    private VectorStore vectorStore;  // ← Dùng lại bean từ config
    
    public String findRelevantExamples(String userQuery) {
        System.out.println("🧠 Tìm kiếm: " + userQuery);
        
        // 1️⃣ GỌI SIMILARITY SEARCH
        // ⭐ SimpleVectorStore sẽ:
        //    - Vector hóa userQuery bằng EmbeddingModel
        //    - Tính similarity với tất cả documents đã lưu
        //    - Return top 5 most similar
        List<Document> similarDocuments = vectorStore.similaritySearch(
            userQuery,  // ← Query text
            5           // ← Top K
        );
        
        if (similarDocuments.isEmpty()) {
            System.out.println("⚠️ Không tìm thấy ví dụ nào!");
            return "No examples found";
        }
        
        // 2️⃣ FORMAT KẾT QUẢ
        StringBuilder result = new StringBuilder();
        result.append("RELEVANT EXAMPLES:\n\n");
        
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
        
        System.out.println("✅ Tìm thấy " + similarDocuments.size() 
            + " ví dụ tương đồng");
        return result.toString();
    }
}
```

### 4️⃣ **AiComparisonService.java** - Sử Dụng

```java
@Service
public class AiComparisonService {
    @Autowired
    private VectorSearchService vectorSearchService;  // ← Inject
    
    public Map<String, Object> handleRequestWithComparison(
        Long sessionId, 
        ChatRequest chatRequest) {
        
        // ✅ BỬC 1: Xây dựng dynamic examples từ vector search
        String dynamicExamples = buildDynamicExamples(
            chatRequest.message()  // ← "Show failed auth attempts"
        );
        
        // Kết quả:
        // "RELEVANT EXAMPLES:
        //  
        //  Example 1:
        //  Question: Show failed authentication attempts
        //  Query: {...}
        //  
        //  Example 2:
        //  ..."
        
        // ✅ BƯỚC 2: Thêm vào LLM Prompt
        String combinedPrompt = systemPrompt 
            + "\n\n" + dynamicExamples;
        
        // ✅ BƯỚC 3: OpenAI/OpenRouter tạo Elasticsearch query
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

---

## 🎯 Tóm Tắt

```
┌─────────────────────────────────────────────────────────────┐
│         VECTOR DATABASE TRANSFORMATION FLOW                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1️⃣ PREPARE DATA                                            │
│     JSON files (2300 Q&A)                                   │
│                                                             │
│  2️⃣ STARTUP (First time)                                   │
│     App starts → VectorStoreConfig created                 │
│                                                             │
│  3️⃣ EMBEDDING (First time)                                 │
│     KnowledgeBaseIndexingService.indexKnowledgeBase()      │
│     For each question: vectorize → store                   │
│                                                             │
│  4️⃣ PERSISTENCE (First time)                               │
│     Save to vector_store.json (125MB)                      │
│                                                             │
│  5️⃣ RUNTIME (Every request)                                │
│     User query → vectorize → similarity search → result    │
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
3. Xóa vector_store.json
4. Restart app
5. Tự động vector hóa + lưu file mới
```

### ❓ Nếu mình muốn thay embedding model?

```
VectorStoreConfig.java:
  
  @Bean
  public VectorStore vectorStore(EmbeddingModel embeddingModel) {
      // Thay OpenAI bằng model khác
      // Spring AI hỗ trợ: OpenAI, Google PaLM, Cohere, ...
  }
```

### ❓ Nếu vector_store.json bị lỗi?

```
1. Xóa file
2. Restart app
3. Tự động tái tạo
```

### ❓ Nếu mình muốn top 10 thay vì top 5?

```
VectorSearchService.java:
  
  List<Document> similarDocuments = vectorStore
    .similaritySearch(userQuery, 10);  // ← Thay đổi số này
```

---

**Generated:** 2025-10-22  
**Version:** 2.0
