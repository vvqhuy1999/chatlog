# 🔄 Hệ Thống Xử Lý Yêu Cầu với Chế Độ So Sánh

## 📋 Tổng Quan

Hệ thống xử lý yêu cầu người dùng với chế độ so sánh, sử dụng **2 API AI khác nhau** (OpenAI và OpenRouter) để tạo truy vấn Elasticsearch và phản hồi cho người dùng. Cả hai AI chạy **song song** để tối ưu thời gian xử lý.

---

## 🏗️ Kiến Trúc Tổng Quan

```
User Request
    ↓
AiServiceImpl (Entry Point)
    ├─→ Khởi tạo ChatClient với Memory (50 messages)
    └─→ Gọi AiComparisonService
        ↓
AiComparisonService (Core Processing)
    ├─→ Chuẩn bị Schema & Prompt
    ├─→ Vector Search (tìm examples từ knowledge base)
    ├─→ Tạo Prompt động với examples
    └─→ Xử lý song song:
        ├─→ OpenAI (Thread 1)
        └─→ OpenRouter (Thread 2)
            ↓
        Mỗi AI:
            ├─→ Generate Elasticsearch Query
            ├─→ Execute Query trên Elasticsearch
            └─→ Generate Response cho user
```

---

## 🔧 Các Thành Phần Chính

### 1. **AiServiceImpl** - Entry Point

**Chức năng:**
- Khởi tạo `ChatClient` với khả năng lưu trữ lịch sử hội thoại
- Sử dụng `MessageWindowChatMemory` với giới hạn **50 tin nhắn** để tối ưu hiệu suất
- Quản lý session và context cho từng cuộc trò chuyện
- Gọi `AiComparisonService` để xử lý comparison mode

**Memory Management:**
- Lưu trữ lịch sử chat trong database (JdbcChatMemoryRepository)
- Tự động giới hạn 50 tin nhắn gần nhất
- Duy trì ngữ cảnh cuộc trò chuyện qua các lần tương tác

---

### 2. **AiComparisonService** - Core Processing

**Chức năng chính:**
- Chuẩn bị schema và tạo System Prompt
- Xây dựng chuỗi ví dụ động (dynamic examples) từ knowledge base
- Xử lý song song OpenAI và OpenRouter

#### 2.1. Chuẩn Bị Schema & Prompt

**SchemaHint** (`utils/SchemaHint.java`):
- Cung cấp field mappings cho log Fortinet theo chuẩn **ECS (Elastic Common Schema)**
- Bao gồm 8 categories: Application/URL/DNS/HTTP/TLS, Device/Host, Network, Security, User, Event, Log, Service
- Đảm bảo AI hiểu đúng cấu trúc dữ liệu để tạo query chính xác

**QueryPromptTemplate** (`utils/QueryPromptTemplate.java`):
- Template engine tạo prompt động cho việc sinh truy vấn Elasticsearch
- Sử dụng dynamic examples từ knowledge base
- Hướng dẫn AI tạo query đúng cấu trúc JSON Elasticsearch
- Xử lý đặc thù log Fortinet firewall theo chuẩn ECS

#### 2.2. Vector Search Process

**Mục đích:** Tìm các examples phù hợp nhất từ knowledge base để bổ sung vào system prompt

**Quy trình:**

1. **Embedding Generation:**
   - Hệ thống chuyển đổi user query thành vector embedding (1536 dimensions)
   - Sử dụng text embedding model (OpenAI)
   - Tất cả examples trong knowledge base đã được pre-computed embeddings và lưu trong Supabase

2. **Similarity Search:**
   - Sử dụng **cosine similarity** để tính toán độ tương đồng giữa user query vector và các example vectors
   - Vector search engine thực hiện tìm kiếm trong không gian nhiều chiều để tìm các examples có semantic meaning gần nhất
   - Tìm kiếm trong Supabase PostgreSQL với pgvector extension

3. **Hybrid Search (70% Semantic + 30% Keyword):**
   - **Semantic Search (70%):** Tìm kiếm dựa trên ý nghĩa ngữ nghĩa
   - **Keyword Search (30%):** Tìm kiếm dựa trên từ khóa trong metadata (keywords array, question, content)
   - Kết hợp kết quả: 8 từ vector similarity + 2 từ keyword matching = Top 10 examples

4. **Dynamic Examples:**
   - Các examples được tìm thấy được format và thêm vào system prompt
   - Giúp AI hiểu rõ hơn về cách tạo truy vấn Elasticsearch phù hợp với yêu cầu người dùng
   - Examples nằm trong `resource/*.json` (ví dụ: `fortigate_queries_full.json` với 2300+ examples)

#### 2.3. Xử Lý Song Song (Parallel Processing)

**OpenAI Thread:**
1. **Generate Elasticsearch Query:**
   - Temperature: **0.0** (đảm bảo kết quả ổn định, không ngẫu nhiên)
   - Mục đích: Tạo query chính xác, nhất quán
   - Sử dụng prompt đã được chuẩn bị với schema, examples, và user query

2. **Execute Query:**
   - Gửi query đến Elasticsearch
   - Nhận kết quả tìm kiếm

3. **Generate Response:**
   - Temperature: **0.3** (chính xác, ít ngẫu nhiên)
   - Mục đích: Phản hồi chính xác, tập trung vào dữ liệu thực tế
   - Tạo phản hồi cho người dùng dựa trên kết quả Elasticsearch

**OpenRouter Thread:**
1. **Generate Elasticsearch Query:**
   - Temperature: **0.5** (cho phép sáng tạo hơn, đa dạng trong cách tiếp cận)
   - Mục đích: Tạo query với approach khác biệt, có thể tìm ra cách tiếp cận tốt hơn
   - Sử dụng cùng prompt như OpenAI

2. **Execute Query:**
   - Gửi query đến Elasticsearch
   - Nhận kết quả tìm kiếm

3. **Generate Response:**
   - Temperature: **0.7** (sáng tạo hơn)
   - Mục đích: Phản hồi đa dạng, có thể cung cấp insights khác biệt
   - Tạo phản hồi cho người dùng dựa trên kết quả Elasticsearch

**Lợi ích của Parallel Processing:**
- Giảm thời gian xử lý: Thay vì chạy tuần tự (OpenAI → OpenRouter), chạy đồng thời
- So sánh kết quả: Người dùng có thể so sánh 2 cách tiếp cận khác nhau
- Tối ưu hiệu suất: Tiết kiệm ~50% thời gian so với xử lý tuần tự

---

## 📊 Temperature Settings

### Query Generation (Tạo Truy Vấn Elasticsearch)

| Provider | Temperature | Mục Đích |
|----------|-------------|----------|
| **OpenAI** | **0.0** | Đảm bảo kết quả ổn định, không ngẫu nhiên. Query chính xác, nhất quán. |
| **OpenRouter** | **0.5** | Cho phép sáng tạo hơn, đa dạng trong cách tiếp cận. Có thể tìm ra cách tốt hơn. |

### Response Generation (Tạo Phản Hồi cho User)

| Provider | Temperature | Mục Đích |
|----------|-------------|----------|
| **OpenAI** | **0.3** | Phản hồi chính xác, tập trung vào dữ liệu thực tế. Ít ngẫu nhiên. |
| **OpenRouter** | **0.7** | Phản hồi sáng tạo hơn, đa dạng. Có thể cung cấp insights khác biệt. |

---

## 🔄 Quy Trình Xử Lý Chi Tiết

### Bước 1: Nhận Request
- User gửi yêu cầu qua API
- `AiServiceImpl` nhận request và gọi `AiComparisonService`

### Bước 2: Chuẩn Bị Prompt
1. Lấy schema từ `SchemaHint.getSchemaHint()`
2. Tìm dynamic examples từ knowledge base qua Vector Search
3. Tạo prompt động với `QueryPromptTemplate.createQueryGenerationPrompt()`
4. Bao gồm:
   - User query
   - Date context (thời gian hiện tại)
   - Schema information (field mappings)
   - Role normalization rules
   - Example log structure
   - Dynamic examples từ knowledge base

### Bước 3: Xử Lý Song Song

**OpenAI Thread:**
```
1. Generate Query (temp=0.0)
   ↓
2. Execute Query trên Elasticsearch
   ↓
3. Generate Response (temp=0.3)
```

**OpenRouter Thread:**
```
1. Generate Query (temp=0.5)
   ↓
2. Execute Query trên Elasticsearch
   ↓
3. Generate Response (temp=0.7)
```

### Bước 4: Tổng Hợp Kết Quả
- Thu thập kết quả từ cả hai AI
- Tính toán metrics (thời gian xử lý, số lượng kết quả, etc.)
- Format response để trả về cho user
- Log chi tiết vào file

---

## 📁 Knowledge Base

**Vị trí:** `src/main/resources/*.json`

**Ví dụ:** `fortigate_queries_full.json` với 2300+ examples

**Nội dung:**
- Mỗi example chứa:
  - `question`: Câu hỏi mẫu
  - `query`: Elasticsearch query tương ứng
  - `keywords`: Danh sách từ khóa liên quan
  - `metadata`: Thông tin bổ sung

**Quá trình Index:**
- Khi ứng dụng khởi động, `KnowledgeBaseIndexingService` tự động:
  1. Đọc các file JSON từ resources
  2. Vector hóa tất cả examples (tạo embeddings)
  3. Lưu vào Supabase PostgreSQL với pgvector extension
  4. Sẵn sàng cho vector search

---

## 🎯 Lợi Ích của Chế Độ So Sánh

1. **Độ Chính Xác:** So sánh 2 cách tiếp cận khác nhau giúp tìm ra query tốt nhất
2. **Hiệu Suất:** Parallel processing giảm thời gian xử lý
3. **Đa Dạng:** 2 AI với temperature khác nhau tạo ra các cách tiếp cận khác nhau
4. **Học Hỏi:** Dynamic examples từ knowledge base giúp AI hiểu rõ hơn về domain
5. **Tối Ưu:** Vector search tìm examples phù hợp nhất, không phải tất cả examples

---

## 📝 Tóm Tắt

Hệ thống sử dụng **2 AI providers** (OpenAI và OpenRouter) để:
- Tạo truy vấn Elasticsearch với temperature khác nhau (0.0 vs 0.5)
- Tạo phản hồi cho user với temperature khác nhau (0.3 vs 0.7)
- Xử lý song song để tối ưu thời gian
- Sử dụng vector search để tìm examples phù hợp từ knowledge base
- Đảm bảo query chính xác với schema ECS và prompt template động

