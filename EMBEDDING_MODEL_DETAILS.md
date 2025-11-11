# 🧠 Embedding Model - Chi Tiết Sử Dụng

## ❓ Câu Hỏi: Project Sử Dụng Model Nào?

**Đáp Án:** Project của bạn sử dụng **`text-embedding-3-small`** (mặc định từ Spring AI OpenAI)

---

## 📍 Chứng Minh Từ Code

### 1. application.yaml - Configuration

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini       # ← Model cho CHAT (LLM)
    
    # ⚠️ KHÔNG CÓ cấu hình embedding model
    # → Tức là dùng model MẶC ĐỊNH
```

**Giải thích:**
- ✅ Có cấu hình cho `chat.options.model: gpt-4o-mini` (cho ChatClient)
- ❌ KHÔNG có cấu hình cho `embedding.options.model`
- 📌 **Khi không cấu hình embedding model → Spring AI dùng DEFAULT**

---

## 🔍 Mô Hình Mặc Định

### text-embedding-3-small

| Thuộc Tính | Chi Tiết |
|-----------|---------|
| **Model Name** | `text-embedding-3-small` |
| **Nhà Cung Cấp** | OpenAI |
| **Loại** | Embedding Model (Vector hóa text) |
| **Kích Thước Output** | 1536 dimensions |
| **Độ Chính Xác** | MTEB score: 62.3% |
| **Tốc Độ** | ~100-200ms per request |
| **Chi Phí** | $0.00002 / 1K tokens (~$0.02/1M tokens) |
| **Tối Đa Input** | 8,191 tokens per request |
| **Đặc Điểm** | Nhỏ gọn, nhanh, tiết kiệm chi phí |

---

## 🔗 Cách Spring AI Biết Dùng Model Nào

### Auto-Configuration Flow

```
1. pom.xml
   └─ spring-ai-starter-model-openai dependency
      │
2. Spring Boot Autoconfiguration
   └─ OpenAiAutoConfiguration.class
      │
3. Check application.yaml
   ├─ spring.ai.openai.api-key: FOUND ✅
   ├─ spring.ai.openai.embedding: NOT FOUND ❌
   │
4. Load Defaults
   └─ DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small"
      │
5. Create Bean
   └─ @Bean OpenAiEmbeddingModel embeddingModel()
      ├─ model = "text-embedding-3-small"
      └─ Ready to use!
```

---

## 📦 Xem Ở Dependency Nào

### pom.xml - Dependencies

```xml
<!-- Spring AI OpenAI Starter -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>

<!-- Spring AI Vector Store -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store</artifactId>
</dependency>

<!-- Spring AI OpenAI (explicit) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai</artifactId>
</dependency>
```

**Cụ Thể:**
- `spring-ai-starter-model-openai` → Chứa **AutoConfiguration**
- `spring-ai-openai` → Chứa **OpenAiEmbeddingModel** (implementation)
- `spring-ai-vector-store` → Chứa **SimpleVectorStore** (storage)

---

## 🎯 Embedding Model Trong Project

### VectorStoreConfig - Nơi Dùng

**File:** `src/main/java/com/example/chatlog/config/VectorStoreConfig.java`

```java
@Configuration
public class VectorStoreConfig {
    
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // ← embeddingModel được AUTO-INJECT
        // ← Spring Boot tự tạo bean từ AutoConfiguration
        // ← Model: text-embedding-3-small (DEFAULT)
        
        SimpleVectorStore vectorStore = SimpleVectorStore
            .builder(embeddingModel)  // ← Inject model
            .build();
        
        if (vectorStoreFile.exists()) {
            vectorStore.load(vectorStoreFile);
        }
        
        return vectorStore;
    }
}
```

**Giải Thích:**
- `EmbeddingModel embeddingModel` - Tham số được inject
- Spring Boot tự tạo bean `OpenAiEmbeddingModel`
- Model mặc định: `text-embedding-3-small`
- SimpleVectorStore dùng model này để vector hóa

---

## 🔄 Cách Nó Hoạt Động

### Khi Embedding Document

```java
// VectorStoreConfig được khởi tạo
SimpleVectorStore vectorStore = SimpleVectorStore
    .builder(embeddingModel)  // ← embeddingModel = OpenAiEmbeddingModel
    .build();

// KnowledgeBaseIndexingService sử dụng
vectorStore.add(documents);  // ← Gọi embeddingModel.embed()
    │
    └─→ Bên trong SimpleVectorStore.add():
        ├─ For each document:
        │  ├─ embeddingModel.embed(document.getContent())
        │  │  └─ OpenAiEmbeddingModel.embed()
        │  │     └─ Gọi OpenAI API: /v1/embeddings
        │  │        └─ Model: text-embedding-3-small
        │  │
        │  └─ Response:
        │     └─ Vector: [-0.234, 0.891, -0.456, ...] (1536 numbers)
        │
        └─ Store vector vào document
```

---

## 📊 Kỹ Thuật Chi Tiết

### OpenAI Embedding API Call

**Request (gửi):**
```
POST https://api.openai.com/v1/embeddings

{
  "input": "Show failed authentication attempts",
  "model": "text-embedding-3-small",
  "encoding_format": "float"
}
```

**Response (nhận):**
```json
{
  "data": [
    {
      "embedding": [
        -0.006929283495992422,
        -0.005336422007530928,
        ...
        -0.03326549753546715,
        -0.01866455376148224
      ],
      "index": 0,
      "object": "embedding"
    }
  ],
  "model": "text-embedding-3-small",
  "object": "list",
  "usage": {
    "prompt_tokens": 8,
    "total_tokens": 8
  }
}
```

---

## ✅ Cách Xác Nhận

### Cách 1: Check Logs Khi Chạy App

```
Console Output:
[OpenAiEmbeddingModel] Using model: text-embedding-3-small
[SimpleVectorStore] Starting embedding 2300 documents...
[SimpleVectorStore] Document 1/2300 embedded successfully
...
```

### Cách 2: Check vector_store.json File

```json
{
  "documents": [
    {
      "content": "Show failed authentication attempts",
      "embedding": [
        -0.006929283495992422,
        -0.005336422007530928,
        ...
      ],
      "metadata": {...}
    }
  ]
}
```

**Verify:** Nếu file đã có `embedding` arrays → Model đã chạy!

---

## 🔧 Nếu Muốn Thay Đổi Model

### Cách 1: Thay Đổi Qua application.yaml

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-large
      chat:
        options:
          model: gpt-4o-mini
```

**Models Khác của OpenAI:**

| Model | Dimensions | Cost | Use Case |
|-------|-----------|------|----------|
| text-embedding-3-small | 1536 | $0.02/M | ✅ Default (project này) |
| text-embedding-3-large | 3072 | $0.13/M | Cần độ chính xác cao hơn |
| text-embedding-ada-002 | 1536 | $0.10/M | Legacy model |

---

## 💡 Tại Sao text-embedding-3-small?

### Lý Do Spring AI Chọn Làm Default

| Tiêu Chí | text-embedding-3-small |
|---------|----------------------|
| Chi Phí | Rẻ nhất ($0.02/M) |
| Tốc Độ | Nhanh nhất (~100ms) |
| Kích Thước | Nhỏ gọn (1536 dim) |
| Độ Chính Xác | Tốt (MTEB 62.3%) |

**Kết Luận:** Balanced giữa chi phí, tốc độ, và độ chính xác!

---

## 📍 Tóm Tắt

```
Project của bạn:
├─ Spring AI Version: 1.0.1
├─ OpenAI API Key: ${OPENAI_API_KEY}
├─ Chat Model: gpt-4o-mini
└─ Embedding Model: text-embedding-3-small ✅
   ├─ Source: spring-ai-starter-model-openai AutoConfiguration
   ├─ Class: OpenAiEmbeddingModel
   ├─ Dimensions: 1536
   ├─ Speed: 100-200ms per request
   └─ Cost: $0.00002 / 1K tokens

Cách hoạt động:
1. VectorStoreConfig inject EmbeddingModel bean
2. EmbeddingModel = OpenAiEmbeddingModel (từ AutoConfiguration)
3. Model name: text-embedding-3-small (mặc định)
4. SimpleVectorStore dùng model này để embed documents
5. Gọi OpenAI API: POST /v1/embeddings
6. Nhận vector 1536-chiều
7. Lưu vào SimpleVectorStore + vector_store.json
```

---

**Generated:** 2025-10-22
