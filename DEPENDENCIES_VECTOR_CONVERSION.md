# 🔧 Dependencies & Vector Conversion Process

## 📌 Mục Lục
1. [Dependencies Chính](#dependencies-chính)
2. [Cách Chúng Hoạt Động](#cách-chúng-hoạt-động)
3. [Luồng Chuyển Đổi Chi Tiết](#luồng-chuyển-đổi-chi-tiết)
4. [Version & Configuration](#version--configuration)

---

## 🏗️ Dependencies Chính

### 📦 pom.xml - Các Thư Viện Quan Trọng

```xml
<!-- VECTOR STORE & EMBEDDING -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

---

## 📊 Dependency Chart

```
┌─────────────────────────────────────────────────────┐
│           TEXT → VECTOR TRANSFORMATION              │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Input: "Show failed authentication attempts"       │
│                                                     │
│         ↓                                           │
│                                                     │
│  ┌──────────────────────────────────────┐           │
│  │  spring-ai-vector-store (Lib 1)      │           │
│  │  - SimpleVectorStore class           │           │
│  │  - Store vectors in memory           │           │
│  │  - Save/Load from JSON file          │           │
│  └──────────────────┬───────────────────┘           │
│                     │                               │
│                     ↓                               │
│                                                     │
│  ┌──────────────────────────────────────┐           │
│  │  spring-ai-openai (Lib 2)            │           │
│  │  - OpenAI API client                 │           │
│  │  - EmbeddingModel interface          │           │
│  │  - Call OpenAI Embedding API         │           │
│  └──────────────────┬───────────────────┘           │
│                     │                               │
│                     ↓                               │
│                                                     │
│  ┌──────────────────────────────────────┐           │
│  │  OpenAI REST API (External Service)  │           │
│  │  POST /v1/embeddings                 │           │
│  │  Input: "Show failed auth attempts"  │           │
│  └──────────────────┬───────────────────┘           │
│                     │                               │
│                     ↓                               │
│                                                     │
│  Output: [-0.234, 0.891, -0.456, ..., ...]          │
│          (1536 dimensions)                          │
│                                                     │
│         ↓                                           │
│                                                     │
│  ┌──────────────────────────────────────┐           │
│  │  spring-ai-vector-store (Lib 1)      │           │
│  │  - Store vector in SimpleVectorStore │           │
│  │  - Create Document object           │            │
│  └──────────────────┬───────────────────┘           │
│                     │                               │
│                     ↓                               │
│                                                     │
│  vector_store.json (File)                           │
│  {                                                  │
│    "documents": [                                   │
│      {                                              │
│        "content": "Show failed...",                 │
│        "embedding": [-0.234, 0.891, ...],           │
│        "metadata": {...}                            │
│      }                                              │
│    ]                                                │
│  }                                                  │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## 🔄 Cách Chúng Hoạt Động

### 1️⃣ Spring AI Vector Store Library

**Artifact:** `spring-ai-vector-store`

**Chức năng:**
- Cung cấp `VectorStore` interface
- Cung cấp `SimpleVectorStore` implementation
- Lưu trữ vectors trong bộ nhớ
- Tìm kiếm semantic (similarity search)
- Save/Load từ file

**Classes:**
```java
// Interface
VectorStore vectorStore;

// Implementation
SimpleVectorStore simpleVectorStore = SimpleVectorStore
    .builder(embeddingModel)
    .build();

// Methods
vectorStore.add(documents);                      // Lưu documents
vectorStore.similaritySearch(query, topK);       // Tìm kiếm
vectorStore.save(file);                          // Lưu file
vectorStore.load(file);                          // Load file
```

**Nó sử dụng:**
- **EmbeddingModel** (từ spring-ai-openai)
- Để vector hóa text
- Lưu embedding trong bộ nhớ

---

### 2️⃣ Spring AI OpenAI Library

**Artifact:** `spring-ai-openai`

**Chức năng:**
- Kết nối tới OpenAI API
- Cung cấp `EmbeddingModel` bean
- Vector hóa text (embedding)
- Gọi LLM (ChatClient)

**Classes:**
```java
// EmbeddingModel bean
@Bean
public EmbeddingModel embeddingModel() {
    // Được inject tự động từ spring-ai-openai
}

// Usage
EmbeddingResponse response = embeddingModel.call(
    new EmbeddingRequest("Show failed auth attempts")
);

List<Float> embedding = response.getResult().getOutput();
// [-0.234, 0.891, -0.456, ...]
```

**Nó làm:**
1. Nhận text từ SimpleVectorStore
2. Gửi tới OpenAI API (POST /v1/embeddings)
3. OpenAI xử lý (tạo vector 1536 chiều)
4. Trả về embedding vector
5. SimpleVectorStore lưu embedding

---

### 3️⃣ Spring AI Starter OpenAI Library

**Artifact:** `spring-ai-starter-model-openai`

**Chức năng:**
- Auto-configuration cho OpenAI
- Tự động inject EmbeddingModel
- Tự động inject ChatClient

**Cấu hình (application.yaml):**
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
```

**Nó làm:**
- Tự động tạo `EmbeddingModel` bean
- Tự động tạo `ChatClient` bean
- Cấu hình API key từ environment

---

## 📋 Luồng Chuyển Đổi Chi Tiết

### Pha 1: Load & Parse JSON

```
┌─────────────────────────────┐
│ File: fortigate_queries.json│
├─────────────────────────────┤
│ [                           │
│   {                         │
│     "question": "Show...",  │
│     "query": {...}          │
│   },                        │
│   ...                       │
│ ]                           │
└──────────────┬──────────────┘
               │
    ┌──────────▼────────────────────┐
    │ Jackson (JSON Processing)     │
    │ objectMapper.readValue()      │
    │ ↓                             │
    │ List<DataExample> examples    │
    └──────────────┬────────────────┘
                   │
           ┌───────▼──────────┐
           │ Document objects │
           │ từng question    │
           └───────┬──────────┘
                   │
                   ↓
        (Pha 2: Embedding)
```

### Pha 2: Embedding (Vector Hóa)

```
FOR EACH Document:

    ┌──────────────────────────────────────┐
    │ Document doc                         │
    │ content: "Show failed auth attempts" │
    └──────────────┬───────────────────────┘
                   │
    ┌──────────────▼──────────────────────┐
    │ SimpleVectorStore.add([doc])         │
    │ ↓                                    │
    │ Calls embeddingModel.embed()         │
    └──────────────┬──────────────────────┘
                   │
    ┌──────────────▼──────────────────────┐
    │ EmbeddingModel (từ spring-ai-openai)│
    │ ↓                                    │
    │ Creates EmbeddingRequest             │
    │ (text: "Show failed auth attempts")  │
    └──────────────┬──────────────────────┘
                   │
    ┌──────────────▼──────────────────────┐
    │ Call OpenAI REST API                │
    │ POST /v1/embeddings                  │
    │ {                                    │
    │   "input": "Show failed auth...",   │
    │   "model": "text-embedding-3-small" │
    │ }                                    │
    └──────────────┬──────────────────────┘
                   │
    ┌──────────────▼──────────────────────┐
    │ OpenAI Server Processing            │
    │ - Tokenize text                      │
    │ - Extract semantic features          │
    │ - Generate 1536-dimensional vector  │
    └──────────────┬──────────────────────┘
                   │
    ┌──────────────▼──────────────────────┐
    │ Response: EmbeddingResponse          │
    │ {                                    │
    │   "data": [                          │
    │     {                                │
    │       "embedding": [                 │
    │         -0.234, 0.891, -0.456, ...  │
    │       ]                              │
    │     }                                │
    │   ]                                  │
    │ }                                    │
    └──────────────┬──────────────────────┘
                   │
    ┌──────────────▼──────────────────────┐
    │ SimpleVectorStore stores:           │
    │ Document {                           │
    │   content: "Show failed auth...",   │
    │   embedding: [-0.234, 0.891, ...],  │
    │   metadata: {...}                   │
    │ }                                    │
    └──────────────────────────────────────┘

⏱️  100-200ms per document (đợi API)
```

### Pha 3: Persistence (Lưu File)

```
┌─────────────────────────────────┐
│ SimpleVectorStore (In-Memory)    │
│ - 2300 documents                │
│ - mỗi document có embedding    │
└──────────────┬──────────────────┘
               │
    ┌──────────▼─────────────────┐
    │ vectorStore.save(file)      │
    │ ↓                           │
    │ Jackson Serialization       │
    │ (ObjectMapper)              │
    └──────────┬──────────────────┘
               │
    ┌──────────▼─────────────────┐
    │ JSON Output                 │
    │ {                           │
    │   "documents": [            │
    │     {                        │
    │       "content": "...",     │
    │       "embedding": [...],   │
    │       "metadata": {...}     │
    │     },                       │
    │     ...                      │
    │   ]                          │
    │ }                            │
    └──────────┬──────────────────┘
               │
    ┌──────────▼─────────────────┐
    │ File: vector_store.json     │
    │ Size: ~125 MB              │
    │ Location: project root      │
    └─────────────────────────────┘
```

---

## 🔍 Runtime: Similarity Search

```
┌────────────────────────────────────┐
│ User Query: "Show failed auth..."  │
└──────────────┬─────────────────────┘
               │
    ┌──────────▼────────────────────┐
    │ VectorStore.similaritySearch()│
    │ (userQuery, topK=5)            │
    └──────────────┬────────────────┘
                   │
    ┌──────────────▼────────────────┐
    │ Call embeddingModel.embed()    │
    │ trên user query                │
    │ ↓                              │
    │ Input: "Show failed auth..."   │
    └──────────────┬────────────────┘
                   │
    ┌──────────────▼────────────────┐
    │ OpenAI API Call                │
    │ (same as indexing)             │
    └──────────────┬────────────────┘
                   │
    ┌──────────────▼────────────────┐
    │ Query Vector Received:         │
    │ [-0.232, 0.893, -0.454, ...]  │
    └──────────────┬────────────────┘
                   │
    ┌──────────────▼────────────────────────┐
    │ Compare with 2300 stored vectors       │
    │ (using Cosine Similarity)              │
    │                                        │
    │ Query vs Doc1: 0.987 ✅                │
    │ Query vs Doc2: 0.985 ✅                │
    │ Query vs Doc3: 0.750 ✅                │
    │ ...                                    │
    │ Query vs Doc2300: 0.001 ❌             │
    └──────────────┬─────────────────────────┘
                   │
    ┌──────────────▼─────────────────────────┐
    │ Sort by similarity score                │
    │ Get top 5                               │
    └──────────────┬─────────────────────────┘
                   │
    ┌──────────────▼─────────────────────────┐
    │ Return 5 Documents                      │
    │ [                                       │
    │   Document(score: 0.987),               │
    │   Document(score: 0.985),               │
    │   Document(score: 0.750),               │
    │   Document(score: 0.680),               │
    │   Document(score: 0.670)                │
    │ ]                                       │
    └─────────────────────────────────────────┘
```

---

## 📦 Version & Configuration

### pom.xml - Spring AI Version

```xml
<properties>
    <spring-ai.version>1.0.1</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Giải thích:**
- **spring-ai-bom:** Bill of Materials (quản lý versions)
- Version 1.0.1: Stable release
- Tất cả spring-ai dependencies tự động dùng version 1.0.1

### application.yaml - OpenAI Configuration

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}  # Từ environment variable
      chat:
        options:
          model: gpt-4o-mini       # Dùng cho LLM
```

**Cấu hình này:**
- Đọc API key từ environment
- Cấu hình model cho chat (không phải embedding)
- Embedding tự động dùng: `text-embedding-3-small`

---

## 🔗 Dependency Relationships

```
┌─────────────────────────────────────┐
│   spring-boot-starter-parent 3.5.5  │
│   (Spring Boot base)                │
└────────────────┬────────────────────┘
                 │
    ┌────────────┴────────────┐
    │                         │
    ▼                         ▼
┌──────────────────┐   ┌─────────────────┐
│ Spring AI        │   │ Spring Boot Web │
│ (all libraries)  │   │ / Data JPA      │
│ (version: 1.0.1)│   │ / Cache etc     │
└────────┬─────────┘   └─────────────────┘
         │
    ┌────┴─────────────────────────────────┐
    │                                      │
    ▼                                      ▼
┌─────────────────────┐   ┌──────────────────────┐
│ spring-ai-openai    │   │ spring-ai-vector-store
│ - EmbeddingModel    │   │ - VectorStore
│ - ChatClient        │   │ - SimpleVectorStore
│ - OpenAI HTTP       │   │ - Similarity Search
└──────────┬──────────┘   └──────────┬───────────┘
           │                        │
           └────────────┬───────────┘
                        │
           ┌────────────▼────────────┐
           │ OpenAI REST API         │
           │ (External Service)      │
           │ /v1/embeddings          │
           │ /v1/chat/completions    │
           └─────────────────────────┘
```

---

## 🛠️ How It Works Together

### Startup (Khởi Động)

```
1. Spring Boot starts
   ↓
2. spring-ai-starter-model-openai
   auto-configures:
   ├─ EmbeddingModel bean
   ├─ ChatClient bean
   └─ OpenAI RestClient
   ↓
3. VectorStoreConfig bean created
   ├─ Injects: EmbeddingModel
   ├─ Creates: SimpleVectorStore
   └─ Checks: vector_store.json exists?
   ↓
4. If NO:
   ├─ KnowledgeBaseIndexingService.indexKnowledgeBase()
   ├─ Reads JSON files
   ├─ Creates Documents
   ├─ For each doc:
   │  ├─ SimpleVectorStore.add(doc)
   │  └─ Calls EmbeddingModel.embed()
   │     → OpenAI API call
   │     → Returns vector
   │     → Stores in SimpleVectorStore
   └─ SimpleVectorStore.save(file)
      → Jackson serializes to JSON
      → Saves as vector_store.json
   ↓
5. If YES:
   ├─ SimpleVectorStore.load(file)
   └─ Ready!
```

### Query (Khi User Gửi)

```
1. User sends query
   ↓
2. AiComparisonService.buildDynamicExamples()
   ↓
3. VectorSearchService.findRelevantExamples()
   ↓
4. VectorStore.similaritySearch(query, topK=5)
   ├─ Calls EmbeddingModel.embed(query)
   │  → OpenAI API call
   │  → Returns query vector
   ├─ Compares with 2300 stored vectors
   │  (using Cosine Similarity)
   └─ Returns top 5 documents
   ↓
5. Format results
   ↓
6. Add to LLM prompt
   ├─ Calls ChatClient (from spring-ai-openai)
   └─ OpenAI generates query
```

---

## 🎯 Tóm Tắt

| Component | Thư Viện | Chức Năng |
|-----------|---------|----------|
| **Vector Storage** | spring-ai-vector-store | SimpleVectorStore, persistence |
| **Embedding** | spring-ai-openai | EmbeddingModel, call OpenAI API |
| **Config** | spring-ai-starter-model-openai | Auto-configure beans |
| **JSON Processing** | jackson-databind | Serialize/deserialize |
| **Framework** | Spring Boot | Dependency injection, auto-config |

---

## 📝 Installation Steps (Nếu Bạn Muốn Thêm)

**Nếu chưa có dependencies (tuy trong project bạn đã có):**

```xml
<!-- pom.xml -->

<!-- 1. Spring AI Vector Store -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store</artifactId>
</dependency>

<!-- 2. Spring AI OpenAI -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai</artifactId>
</dependency>

<!-- 3. Spring AI OpenAI Starter (optional but recommended) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>

<!-- 4. Jackson (usually comes with Spring Boot) -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

**application.yaml Configuration:**

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}      # Set from env var
      chat:
        options:
          model: gpt-4o-mini          # For LLM
          # Embedding model is automatic: text-embedding-3-small
```

---

**Generated:** 2025-10-22  
**Reference:** Project pom.xml v1.0.1 Spring AI
