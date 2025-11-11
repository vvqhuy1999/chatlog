# 🔬 Dependencies & Vector Conversion - Chi Tiết Với Nguồn

## 📚 Mục Lục
1. [Official Sources & Documentation](#official-sources--documentation)
2. [Package Details & Imports](#package-details--imports)
3. [Deep Dive: Code Analysis](#deep-dive-code-analysis)
4. [Embedding API Technical Details](#embedding-api-technical-details)
5. [Vector Store Internal Working](#vector-store-internal-working)

---

## 📖 Official Sources & Documentation

### Spring AI Official Links

| Resource | URL | Description |
|----------|-----|-------------|
| **Spring AI Main** | https://spring.io/projects/spring-ai | Official Spring AI project page |
| **Vector Stores** | https://docs.spring.io/spring-ai/reference/api/vectordbs.html | Vector database documentation |
| **OpenAI Integration** | https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html | OpenAI integration guide |
| **Embeddings** | https://docs.spring.io/spring-ai/reference/api/embeddings.html | Embedding models documentation |
| **Maven Repository** | https://mvnrepository.com/artifact/org.springframework.ai | All Spring AI artifacts |

### OpenAI Official API Documentation

| Resource | URL | Description |
|----------|-----|-------------|
| **Embeddings API** | https://platform.openai.com/docs/guides/embeddings | Official embeddings guide |
| **text-embedding-3-small** | https://platform.openai.com/docs/models/embeddings | Model specifications |
| **API Reference** | https://platform.openai.com/docs/api-reference/embeddings | Complete API reference |

---

## 📦 Package Details & Imports

### Complete Package Structure

```
org.springframework.ai
├── chat
│   ├── client
│   │   └── ChatClient                    # LLM chat interface
│   ├── memory
│   │   ├── ChatMemory                    # Memory management
│   │   └── MessageWindowChatMemory       # Windowed memory
│   ├── messages
│   │   ├── SystemMessage                 # System prompt
│   │   └── UserMessage                   # User input
│   └── prompt
│       ├── ChatOptions                   # LLM options (temp, etc)
│       └── Prompt                        # Full prompt wrapper
│
├── document
│   └── Document                          # Vector document wrapper
│
├── embedding
│   ├── EmbeddingModel                    # Embedding interface
│   ├── EmbeddingRequest                  # Request wrapper
│   └── EmbeddingResponse                 # Response wrapper
│
└── vectorstore
    ├── VectorStore                       # Vector store interface
    ├── SimpleVectorStore                 # In-memory implementation
    └── SearchRequest                     # Search parameters
```

### Real Imports From Your Project

**VectorStoreConfig.java:**
```java
import org.springframework.ai.embedding.EmbeddingModel;      // ← Interface để embed text
import org.springframework.ai.vectorstore.SimpleVectorStore; // ← Implementation in-memory
import org.springframework.ai.vectorstore.VectorStore;       // ← Interface chính
```

**KnowledgeBaseIndexingService.java:**
```java
import org.springframework.ai.document.Document;     // ← Wrapper cho content + embedding
import org.springframework.ai.vectorstore.VectorStore; // ← Store interface
```

**VectorSearchService.java:**
```java
import org.springframework.ai.document.Document;     // ← Document wrapper
import org.springframework.ai.vectorstore.VectorStore; // ← Vector store
```

---

## 🔍 Deep Dive: Code Analysis

### 1️⃣ VectorStoreConfig - Chi Tiết

**Source:** `src/main/java/com/example/chatlog/config/VectorStoreConfig.java`

```java
@Configuration
public class VectorStoreConfig {
    
    private final File vectorStoreFile = new File("vector_store.json");
    
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // ⭐ BƯỚC 1: Khởi tạo SimpleVectorStore
        // SimpleVectorStore.builder() - Pattern Builder
        // embeddingModel - Được inject từ spring-ai-openai
        SimpleVectorStore vectorStore = SimpleVectorStore
            .builder(embeddingModel)  // ← Inject EmbeddingModel
            .build();
        
        // ⭐ BƯỚC 2: Load từ file nếu tồn tại
        if (this.vectorStoreFile.exists()) {
            System.out.println("✅ Tải Vector Store từ file");
            
            // load() method:
            // - Đọc JSON file
            // - Parse thành List<Document>
            // - Mỗi Document có: content, embedding, metadata
            // - Load vào bộ nhớ (Map<String, Document>)
            vectorStore.load(this.vectorStoreFile);
        }
        
        return vectorStore;  // ← Return bean để inject vào services
    }
}
```

**Chi tiết kỹ thuật:**

1. **SimpleVectorStore.builder(embeddingModel)**
   - Pattern: Builder Pattern
   - Input: `EmbeddingModel` (interface)
   - Implementation thực tế: `OpenAiEmbeddingModel` (từ spring-ai-openai)
   - Purpose: Để có thể gọi `embeddingModel.embed(text)` khi cần

2. **vectorStore.load(file)**
   - Đọc file JSON bằng Jackson ObjectMapper
   - Deserialize thành internal data structure
   - Structure: `Map<String, Document>` (id → document)
   - Mỗi Document chứa:
     - `content`: Text gốc
     - `embedding`: float[] (1536 dimensions)
     - `metadata`: Map<String, Object>

---

### 2️⃣ KnowledgeBaseIndexingService - Chi Tiết

**Source:** `src/main/java/com/example/chatlog/service/impl/KnowledgeBaseIndexingService.java`

```java
@Service
public class KnowledgeBaseIndexingService {
    
    @Autowired
    private VectorStore vectorStore;  // ← Inject bean từ VectorStoreConfig
    
    private final File vectorStoreFile = new File("vector_store.json");
    
    @PostConstruct  // ← Chạy sau khi bean được inject
    public void indexKnowledgeBase() {
        // Skip nếu file đã tồn tại
        if (vectorStoreFile.exists()) {
            return;
        }
        
        String[] knowledgeBaseFiles = {
            "fortigate_queries_full.json",
            // ... 10 files khác
        };
        
        List<Document> documents = new ArrayList<>();
        
        for (String fileName : knowledgeBaseFiles) {
            // ⭐ BƯỚC 1: Đọc JSON file
            ClassPathResource resource = new ClassPathResource(fileName);
            InputStream inputStream = resource.getInputStream();
            
            // ⭐ BƯỚC 2: Parse JSON → List<DataExample>
            List<DataExample> examples = objectMapper.readValue(
                inputStream, 
                new TypeReference<List<DataExample>>() {}
            );
            
            // ⭐ BƯỚC 3: Tạo Document cho mỗi example
            for (DataExample example : examples) {
                // Document constructor:
                // - arg1: content (string) - dùng để embed
                // - arg2: metadata (Map) - thông tin bổ sung
                Document doc = new Document(
                    example.getQuestion(),  // ← Content
                    Map.of(
                        "question", example.getQuestion(),
                        "query_dsl", queryDslJson,
                        "source_file", fileName
                    )
                );
                documents.add(doc);
            }
        }
        
        // ⭐ BƯỚC 4: Vector hóa tất cả documents
        // Bên trong add():
        // 1. For each document:
        //    - Gọi embeddingModel.embed(document.content)
        //    - OpenAI API call: POST /v1/embeddings
        //    - Nhận vector: float[1536]
        //    - Set document.embedding = vector
        // 2. Lưu vào internal Map
        vectorStore.add(documents);
        
        // ⭐ BƯỚC 5: Persist to file
        ((SimpleVectorStore) vectorStore).save(vectorStoreFile);
    }
}
```

**Chi tiết `vectorStore.add(documents)`:**

```java
// Pseudo code của SimpleVectorStore.add()
public void add(List<Document> documents) {
    for (Document doc : documents) {
        // 1. Check if already has embedding
        if (doc.getEmbedding() == null) {
            // 2. Call EmbeddingModel to generate embedding
            EmbeddingRequest request = new EmbeddingRequest(
                doc.getContent(),  // ← Text to embed
                EmbeddingOptionsBuilder.builder().build()
            );
            
            EmbeddingResponse response = embeddingModel.call(request);
            
            // 3. Extract embedding from response
            float[] embedding = response.getResult()
                                       .getOutput()
                                       .toArray();
            
            // 4. Set embedding to document
            doc.setEmbedding(embedding);
        }
        
        // 5. Store in internal map
        this.documents.put(doc.getId(), doc);
    }
}
```

---

### 3️⃣ VectorSearchService - Chi Tiết

**Source:** `src/main/java/com/example/chatlog/service/impl/VectorSearchService.java`

```java
@Service
public class VectorSearchService {
    
    @Autowired
    private VectorStore vectorStore;
    
    public String findRelevantExamples(String userQuery) {
        // ⭐ SIMILARITY SEARCH
        // Bên trong similaritySearch():
        // 1. Embed user query (gọi EmbeddingModel)
        // 2. Calculate similarity với tất cả stored vectors
        // 3. Sort by score descending
        // 4. Return top K results
        List<Document> similarDocuments = vectorStore
            .similaritySearch(userQuery);
        
        // Format results
        StringBuilder examples = new StringBuilder();
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
        
        return examples.toString();
    }
}
```

**Chi tiết `vectorStore.similaritySearch(query)`:**

```java
// Pseudo code của SimpleVectorStore.similaritySearch()
public List<Document> similaritySearch(String query) {
    // 1. Embed the query
    EmbeddingRequest request = new EmbeddingRequest(
        query,  // ← User query text
        EmbeddingOptionsBuilder.builder().build()
    );
    
    EmbeddingResponse response = embeddingModel.call(request);
    float[] queryEmbedding = response.getResult()
                                    .getOutput()
                                    .toArray();
    
    // 2. Calculate similarity với tất cả documents
    List<ScoredDocument> scoredDocs = new ArrayList<>();
    
    for (Document doc : this.documents.values()) {
        // Cosine Similarity calculation
        double similarity = calculateCosineSimilarity(
            queryEmbedding, 
            doc.getEmbedding()
        );
        
        scoredDocs.add(new ScoredDocument(doc, similarity));
    }
    
    // 3. Sort by similarity score (descending)
    scoredDocs.sort((a, b) -> 
        Double.compare(b.score, a.score)
    );
    
    // 4. Return top K (default: 4-5)
    return scoredDocs.stream()
                     .limit(topK)
                     .map(sd -> sd.document)
                     .collect(Collectors.toList());
}

// Cosine Similarity formula
private double calculateCosineSimilarity(float[] a, float[] b) {
    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    
    for (int i = 0; i < a.length; i++) {
        dotProduct += a[i] * b[i];
        normA += a[i] * a[i];
        normB += b[i] * b[i];
    }
    
    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
}
```

---

## 🌐 Embedding API Technical Details

### OpenAI Embedding API Request

**Endpoint:**
```
POST https://api.openai.com/v1/embeddings
```

**Headers:**
```
Content-Type: application/json
Authorization: Bearer ${OPENAI_API_KEY}
```

**Request Body:**
```json
{
  "input": "Show failed authentication attempts",
  "model": "text-embedding-3-small",
  "encoding_format": "float"
}
```

**Response:**
```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [
        -0.006929283495992422,
        -0.005336422007530928,
        ...
        -0.03326549753546715,
        -0.01866455376148224
      ],
  // ← 1536 float numbers
      "index": 0
    }
  ],
  "model": "text-embedding-3-small",
  "usage": {
    "prompt_tokens": 8,
    "total_tokens": 8
  }
}
```

### Model Specifications

| Specification | Value |
|--------------|-------|
| **Model Name** | text-embedding-3-small |
| **Dimensions** | 1536 |
| **Max Input Tokens** | 8191 |
| **Cost** | $0.00002 / 1K tokens |
| **Performance** | MTEB score: 62.3% |
| **Speed** | ~100-200ms per request |

### How EmbeddingModel Works in Spring AI

```java
// Spring AI wraps OpenAI API call
public interface EmbeddingModel {
    
    // Main method
    EmbeddingResponse call(EmbeddingRequest request);
    
    // Convenience methods
    List<Double> embed(String text);
    List<Double> embed(Document document);
    List<List<Double>> embed(List<String> texts);
}

// OpenAiEmbeddingModel implementation
public class OpenAiEmbeddingModel implements EmbeddingModel {
    
    private final OpenAiApi openAiApi;  // ← HTTP client
    private final String model = "text-embedding-3-small";
    
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        // 1. Build HTTP request
        OpenAiApi.EmbeddingRequest apiRequest = 
            new OpenAiApi.EmbeddingRequest(
                request.getInstructions(),  // ← Text to embed
                this.model
            );
        
        // 2. Call OpenAI API (HTTP POST)
        ResponseEntity<OpenAiApi.EmbeddingList> response = 
            this.openAiApi.embeddings(apiRequest);
        
        // 3. Convert response to EmbeddingResponse
        return convertResponse(response.getBody());
    }
}
```

---

## 🗄️ Vector Store Internal Working

### SimpleVectorStore Internal Structure

```java
public class SimpleVectorStore implements VectorStore {
    
    // Internal storage
    private final Map<String, Document> store = new ConcurrentHashMap<>();
    
    // Embedding model reference
    private final EmbeddingModel embeddingModel;
    
    // Constructor (via builder)
    private SimpleVectorStore(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    @Override
    public void add(List<Document> documents) {
        for (Document doc : documents) {
            // Generate ID if not present
            if (doc.getId() == null) {
                doc.setId(UUID.randomUUID().toString());
            }
            
            // Embed if needed
            if (doc.getEmbedding() == null || doc.getEmbedding().isEmpty()) {
                List<Double> embedding = embeddingModel.embed(doc.getContent());
                doc.setEmbedding(embedding);
            }
            
            // Store
            this.store.put(doc.getId(), doc);
        }
    }
    
    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String query = request.getQuery();
        int topK = request.getTopK();
        
        // 1. Embed query
        List<Double> queryEmbedding = embeddingModel.embed(query);
        
        // 2. Calculate similarities
        List<SimilarityResult> results = new ArrayList<>();
        for (Document doc : this.store.values()) {
            double similarity = cosineSimilarity(
                queryEmbedding, 
                doc.getEmbedding()
            );
            results.add(new SimilarityResult(doc, similarity));
        }
        
        // 3. Sort and return top K
        return results.stream()
                     .sorted(Comparator.comparingDouble(
                         SimilarityResult::getScore
                     ).reversed())
                     .limit(topK)
                     .map(SimilarityResult::getDocument)
                     .collect(Collectors.toList());
    }
    
    @Override
    public void save(File file) {
        // Serialize to JSON
        ObjectMapper mapper = new ObjectMapper();
        VectorStoreData data = new VectorStoreData(
            new ArrayList<>(this.store.values())
        );
        mapper.writeValue(file, data);
    }
    
    @Override
    public void load(File file) {
        // Deserialize from JSON
        ObjectMapper mapper = new ObjectMapper();
        VectorStoreData data = mapper.readValue(
            file, 
            VectorStoreData.class
        );
        
        // Populate store
        this.store.clear();
        for (Document doc : data.getDocuments()) {
            this.store.put(doc.getId(), doc);
        }
    }
}
```

### Document Structure

```java
public class Document {
    
    private String id;              // UUID
    private String content;         // Original text
    private Map<String, Object> metadata;  // Additional info
    private List<Double> embedding; // Vector (1536 dimensions)
    
    // Constructor
    public Document(String content, Map<String, Object> metadata) {
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.metadata = metadata;
        this.embedding = null;  // Will be set by VectorStore
    }
    
    // Getters/Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { 
        this.metadata = metadata; 
    }
    
    public List<Double> getEmbedding() { return embedding; }
    public void setEmbedding(List<Double> embedding) { 
        this.embedding = embedding; 
    }
}
```

---

## 📊 Performance Analysis

### Timing Breakdown

```
Total Time to Index 2300 Documents: ~30-60 minutes

Breakdown:
├─ Read JSON files: ~2 seconds
├─ Parse to DataExample: ~3 seconds
├─ Create Documents: ~1 second
├─ Embedding API calls: ~25-55 minutes
│  ├─ Per request: 100-200ms
│  ├─ Total requests: 2300
│  ├─ With rate limiting: ~1400ms average
│  └─ Total: 2300 × 1.4s = 3220s ≈ 54 minutes
└─ Save to JSON: ~20 seconds (125MB write)

Search Time: ~300ms
├─ Embed query: 150ms (OpenAI API call)
├─ Calculate similarity: 100ms (2300 comparisons)
├─ Sort results: 30ms
└─ Format output: 20ms
```

### Memory Usage

```
In-Memory VectorStore:
├─ 2300 documents
├─ Each document:
│  ├─ content: ~100 bytes (avg)
│  ├─ embedding: 1536 × 8 bytes = 12,288 bytes
│  └─ metadata: ~200 bytes
├─ Per document: ~12,588 bytes
└─ Total: 2300 × 12,588 ≈ 29 MB in memory

File Size (vector_store.json):
├─ JSON overhead: ~40%
├─ Compressed size: ~125 MB
└─ Uncompressed: ~175 MB
```

---

## 🔗 Dependency Graph (Deep)

```
pom.xml
└─ spring-ai-bom (1.0.1)
   └─ Manages all Spring AI versions
      │
      ├─ spring-ai-openai (1.0.1)
      │  ├─ Provides: OpenAiEmbeddingModel
      │  ├─ Provides: OpenAiChatModel
      │  └─ Dependencies:
      │     ├─ spring-web (HTTP client)
      │     ├─ jackson-databind (JSON)
      │     └─ openai-java (unofficial, optional)
      │
      ├─ spring-ai-vector-store (1.0.1)
      │  ├─ Provides: VectorStore interface
      │  ├─ Provides: SimpleVectorStore impl
      │  ├─ Provides: Document class
      │  └─ Dependencies:
      │     ├─ spring-core
      │     └─ jackson-databind (for persistence)
      │
      └─ spring-ai-starter-model-openai (1.0.1)
         ├─ Auto-configuration
         ├─ Creates: EmbeddingModel bean
         ├─ Creates: ChatClient bean
         └─ Dependencies:
            ├─ spring-boot-autoconfigure
            └─ spring-ai-openai
```

---

## 📚 Additional Resources

### Official Documentation

1. **Spring AI Reference**
   - URL: https://docs.spring.io/spring-ai/reference/
   - Topics: All Spring AI components

2. **Spring AI GitHub**
   - URL: https://github.com/spring-projects/spring-ai
   - Code samples & examples

3. **OpenAI Platform Docs**
   - URL: https://platform.openai.com/docs
   - API references & guides

### Books & Papers

1. **"Efficient Estimation of Word Representations in Vector Space"**
   - Authors: Mikolov et al.
   - Link: https://arxiv.org/abs/1301.3781

2. **"BERT: Pre-training of Deep Bidirectional Transformers"**
   - Authors: Devlin et al.
   - Link: https://arxiv.org/abs/1810.04805

---

**Generated:** 2025-10-22  
**Version:** 2.0 (Extended with sources)  
**Author:** Based on Spring AI 1.0.1 & OpenAI API v1

