# 📚 Vector Store - Giải Thích Dễ Hiểu

## 🎯 Vector là gì? (Đơn giản nhất)

Hãy tưởng tượng:

```
Câu 1: "Show failed authentication attempts"
Câu 2: "Display unsuccessful login events"
Câu 3: "Get information about users"
```

**Câu 1 và 2** có ý nghĩa gần giống nhau (cả hai đều hỏi về failed login).  
**Câu 3** hoàn toàn khác.

### Vector là cách máy tính **biểu diễn và so sánh** những ý nghĩa đó.

---

## 🧠 Cách hoạt động (Từng bước)

### Bước 1: Chuyển đổi Text thành Vector (Embedding)

```
Text: "Show failed authentication attempts"
                    ↓
            Embedding Model
                    ↓
Vector: [0.2, -0.5, 0.8, 0.1, -0.3, ...]  ← 1536 con số!
```

**Embedding Model** là một AI model chuyên việc chuyển text thành con số.

- ✅ OpenAI's embedding model
- ✅ Cohere
- ✅ Google PaLM
- v.v.

### Bước 2: Lưu trữ Vector (Vector Store)

```
┌─────────────────────────────────┐
│    Vector Store (SimpleVectorStore)    │
├─────────────────────────────────┤
│ Question 1: [0.2, -0.5, 0.8...] │
│ Question 2: [0.1, -0.4, 0.7...] │  ← Gần với Question 1
│ Question 3: [0.9, 0.1, -0.2...] │  ← Khác hơn
│ Question 4: [0.3, -0.6, 0.75..] │  ← Gần với Question 1
└─────────────────────────────────┘
```

### Bước 3: Tìm kiếm (Similarity Search)

Khi người dùng hỏi: **"Display unsuccessful logins"**

```
User Query: "Display unsuccessful logins"
                    ↓
            Embedding Model
                    ↓
        Vector: [0.18, -0.48, 0.82, ...]
                    ↓
        Tính độ giống với tất cả vectors
                    ↓
         So sánh độ tương đồng (similarity)
                    ↓
      Return top 5 câu hỏi gần nhất ✅
```

---

## 📊 Ví dụ cụ thể trong project của bạn

### Lần chạy ứng dụng thứ 1️⃣

```
1️⃣ Startup ứng dụng
   ↓
2️⃣ Spring khởi động VectorStoreConfig
   ├─► Tạo EmbeddingModel (OpenAI)
   └─► Tạo SimpleVectorStore
   ↓
3️⃣ KnowledgeBaseIndexingService chạy (@PostConstruct)
   ├─► Kiểm tra: vector_store.json tồn tại?
   └─► KHÔNG → tiếp tục
   ↓
4️⃣ Đọc 11 file JSON từ resources
   ├─► fortigate_queries_full.json (500+ questions)
   ├─► advanced_security_scenarios.json (200+ questions)
   └─► ... (9 file khác)
   ↓
5️⃣ Với mỗi question:
   ├─► "Show failed authentication attempts"
   │    ↓
   │    OpenAI Embedding Model
   │    ↓
   │    [0.2, -0.5, 0.8, ...] ← Vector!
   │    ↓
   │    Lưu vào SimpleVectorStore
   │
   ├─► "Display unsuccessful login events"
   │    ↓ (tương tự)
   │
   └─► ... (2300+ questions)
   ↓
6️⃣ Lưu tất cả vectors xuống file
   └─► vector_store.json (125MB)
   ↓
7️⃣ ✅ Xong! Sẵn sàng phục vụ
   
   Thời gian: 30-60 giây
```

### Lần chạy ứng dụng thứ 2️⃣ trở đi

```
1️⃣ Startup ứng dụng
   ↓
2️⃣ VectorStoreConfig chạy
   ├─► Kiểm tra: vector_store.json tồn tại?
   └─► CÓ → Load từ file!
   ↓
3️⃣ SimpleVectorStore được điền dữ liệu từ file
   ↓
4️⃣ ✅ Ready ngay!
   
   Thời gian: 1-2 giây
```

---

## 🤔 Khi người dùng gửi query

### User hỏi: "Show failed authentication attempts"

```
1️⃣ Request đến AiComparisonService
   ↓
2️⃣ buildDynamicExamples(userQuery)
   ↓
3️⃣ VectorSearchService.findRelevantExamples()
   ↓
4️⃣ vectorStore.similaritySearch("Show failed authentication attempts")
   │
   ├─► Convert query thành vector
   │    └─► [0.19, -0.49, 0.81, ...]
   │
   ├─► Tính độ giống (similarity) với tất cả vectors:
   │    ✅ "Display unsuccessful login events" → 0.95 (rất giống!)
   │    ✅ "Get failed auth errors" → 0.92 (rất giống!)
   │    ✅ "Show login failures" → 0.89 (giống)
   │    ✅ "List auth attempts" → 0.85 (khá giống)
   │    ✅ "Get user list" → 0.45 (ít giống)
   │
   └─► Return top 5 ✅
   ↓
5️⃣ Format kết quả:
   "RELEVANT EXAMPLES FROM KNOWLEDGE BASE (Semantic Search):
    
    Example 1:
    Question: Display unsuccessful login events
    Query: {...elasticsearch query...}
    
    Example 2:
    Question: Get failed auth errors
    Query: {...elasticsearch query...}
    
    ..."
   ↓
6️⃣ Thêm vào LLM prompt
   ↓
7️⃣ LLM (OpenAI/OpenRouter) tạo Elasticsearch query tốt hơn
   ↓
8️⃣ Return kết quả cho user ✅
```

---

## 🎯 Lợi ích của Vector Search

### ❌ Trước (Keyword Matching):
```
User: "Show failed authentication attempts"
      ↓
Tìm từ khóa: "show", "failed", "authentication", "attempts"
      ↓
Chỉ match câu hỏi có chính xác từ khóa đó
      ↓
Bỏ sót: "Display unsuccessful login events"
        (không có từ "failed" và "authentication")
      ↓
Kết quả: ❌ Không tốt (60-70% accuracy)
```

### ✅ Sau (Semantic Search):
```
User: "Show failed authentication attempts"
      ↓
Hiểu MỌI ý nghĩa của câu
      ↓
Tìm tất cả câu có ý nghĩa gần giống
      ↓
Tìm được: "Display unsuccessful login events"
         (hiểu là cùng một ý!)
      ↓
Kết quả: ✅ Tốt (85-95% accuracy)
```

---

## 💾 Persistence (Lưu trữ)

### Tại sao cần lưu vector_store.json?

```
❌ Không lưu:
  - Mỗi lần restart phải vector hóa lại (30-60 giây)
  - Tốn bandwidth (tính embedding từ OpenAI)
  - Chậm!

✅ Lưu vào file:
  - Startup nhanh (1-2 giây)
  - Không cần tính embedding lại
  - Tiết kiệm chi phí
  - Dữ liệu bền vững!
```

### vector_store.json chứa gì?

```json
{
  "documents": [
    {
      "content": "Show failed authentication attempts",
      "metadata": {
        "question": "Show failed authentication attempts",
        "query_dsl": "{...elasticsearch query...}",
        "source_file": "fortigate_queries_full.json"
      },
      "embedding": [0.2, -0.5, 0.8, ...] ← 1536 numbers
    },
    {
      "content": "Display unsuccessful login events",
      "metadata": {...},
      "embedding": [0.1, -0.4, 0.7, ...]
    },
    ...
  ]
}
```

---

## 🔄 Quy trình hoàn chỉnh

```
                    ┌─────────────────────────────────┐
                    │     Knowledge Base Files        │
                    │  (11 JSON files, 2300+ Q&A)    │
                    └────────────┬────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │  Embedding Model       │
                    │  (OpenAI, Cohere...)   │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   Vector Store         │
                    │  (In-memory)           │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │  vector_store.json     │
                    │  (Persistent)          │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   User Query           │
                    │  (Runtime)             │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │  Similarity Search     │
                    │  (Find top 5)          │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   Add to LLM Prompt    │
                    │  (Context)             │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   Better Response      │
                    │  (User gets answer)    │
                    └────────────────────────┘
```

---

## ❓ FAQs

### Q: Vector là gì?
**A:** Vector là một mảng con số đại diện ý nghĩa của một câu văn. Máy tính dùng nó để so sánh độ giống nhau giữa các câu.

### Q: Tại sao phải lưu vector_store.json?
**A:** Để tránh phải tính embedding lại mỗi lần startup. Tiết kiệm thời gian (từ 30s → 1s) và chi phí API.

### Q: Chậm thế nào khi tìm kiếm?
**A:** 100-500ms cho semantic search. Nhanh lắm!

### Q: Có thể add thêm knowledge base được không?
**A:** Có! Thêm file JSON vào `src/main/resources`, cập nhật `KnowledgeBaseIndexingService`, delete `vector_store.json`, restart ứng dụng.

### Q: Vector store có lớp không?
**A:** Có, ~125MB cho 2300 examples. Tùy theo số lượng examples.

---

## 🎓 Tóm tắt 1 câu

> **Vector Store** là hệ thống lưu trữ các "bản đồ ý nghĩa" của tất cả Q&A, cho phép tìm kiếm ngữ nghĩa nhanh chóng và chính xác thay vì chỉ khớp từ khóa.

---

**Hiểu rồi chứ?** 🚀 Bạn có câu hỏi gì thêm không?
