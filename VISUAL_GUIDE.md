# 🎨 Vector Database - Visual Guide (Hình Ảnh Minh Họa)

## 📋 Mục Lục
1. [Quy Trình Chuyển Đổi](#quy-trình-chuyển-đổi)
2. [Architecture](#architecture)
3. [Data Flow](#data-flow)
4. [Search Process](#search-process)

---

## 🔄 Quy Trình Chuyển Đổi

### Pha 1: Khởi Động & Load Data

```
                    ┌──────────────┐
                    │  App Starts  │
                    └──────┬───────┘
                           │
                ┌──────────▼──────────┐
                │  VectorStoreConfig  │
                │  @Bean @Configuration
                └──────────┬──────────┘
                           │
              ┌────────────▼────────────┐
              │ Check vector_store.json │
              │    exists?              │
              └────────┬──────────┬─────┘
                       │          │
                    YES│          │NO
                       │          │
            ┌──────────▼──┐   ┌──▼─────────────────────┐
            │ Load from   │   │ KnowledgeBaseIndexing  │
            │ file        │   │ Service @PostConstruct │
            │             │   └──┬────────────────────┘
            │ ⏰ 1-2s     │      │
            │             │      │
            └──────────┬──┘   ┌──▼────────────────────┐
                       │      │ Read 11 JSON Files   │
                       │      │ Parse DataExamples   │
                       │      │ Create Documents     │
                       │      │ Vector Hóa (API call)│
                       │      │                      │
                       │      │ ⏰ 30-60 phút        │
                       │      │                      │
                       │      │ Save to              │
                       │      │ vector_store.json    │
                       │      └──┬────────────────────┘
                       │         │
                       └────┬────┘
                            │
                    ┌───────▼──────┐
                    │ Ready to Use!│
                    │ VectorStore  │
                    │ in memory    │
                    └──────────────┘
```

---

### Pha 2: Vector Hóa Chi Tiết

```
┌──────────────────────────────────────────────────────────┐
│                 VECTORIZATION PROCESS                     │
├──────────────────────────────────────────────────────────┤
│                                                           │
│  File: fortigate_queries_full.json                       │
│  ┌────────────────────────────────────────┐              │
│  │ Question 1: "Show failed auth..."      │              │
│  │ Question 2: "Display unsuccessful..."  │              │
│  │ Question 3: "Get failed access..."     │              │
│  │ ...                                    │              │
│  │ Question 500: ...                      │              │
│  └──────────────┬────────────────────────┘              │
│                 │                                        │
│              FOR EACH QUESTION                           │
│                 │                                        │
│      ┌──────────▼──────────┐                            │
│      │ Send to OpenAI API  │                            │
│      │ Embedding Endpoint  │                            │
│      └──────────┬──────────┘                            │
│                 │                                        │
│   ┌─────────────▼────────────┐                          │
│   │ OpenAI Returns Vector:   │                          │
│   │ [-0.234, 0.891, -0.456, │                          │
│   │  0.123, 0.045, ..., ... │  1536 numbers            │
│   │  ]                       │                          │
│   └─────────────┬────────────┘                          │
│                 │                                        │
│   ┌─────────────▼────────────┐                          │
│   │ Create Document with:    │                          │
│   │ - content (question)     │                          │
│   │ - embedding (vector)     │                          │
│   │ - metadata (query_dsl)   │                          │
│   └─────────────┬────────────┘                          │
│                 │                                        │
│   ┌─────────────▼────────────┐                          │
│   │ Store in VectorStore     │                          │
│   │ (in-memory)              │                          │
│   └─────────────────────────┘                          │
│                                                           │
│  100-200ms per question                                  │
│  Total for 500: ~50-100 seconds                          │
│                                                           │
└──────────────────────────────────────────────────────────┘
```

---

### Pha 3: Lưu Trữ (Persistence)

```
┌────────────────────────────────────────────────────────┐
│          VECTOR STORE TO FILE PERSISTENCE               │
├────────────────────────────────────────────────────────┤
│                                                         │
│  SimpleVectorStore (In-Memory)                          │
│  ┌──────────────────────────────────┐                  │
│  │ Doc 1: content, embedding, meta   │                  │
│  │ Doc 2: content, embedding, meta   │                  │
│  │ ...                               │                  │
│  │ Doc 2300: content, embedding, meta│                  │
│  └──────────┬───────────────────────┘                  │
│             │                                           │
│             │ vectorStore.save(file)                    │
│             ▼                                           │
│  ┌──────────────────────────────────┐                  │
│  │ vector_store.json (125 MB)        │                  │
│  │ - JSON format                     │                  │
│  │ - Contains all embeddings         │                  │
│  │ - On disk (persistent)            │                  │
│  └──────────────────────────────────┘                  │
│                                                         │
│  Benefits:                                              │
│  ✓ Fast load on restart (1-2s)                          │
│  ✓ No API calls needed again                            │
│  ✓ Save costs                                           │
│                                                         │
└────────────────────────────────────────────────────────┘
```

---

## 🏗️ Architecture

```
User sends query
       │
       ▼
REST Controller
       │
       ▼
AiComparisonService.handleRequestWithComparison()
       │
       ▼
buildDynamicExamples(userQuery)
       │
       ▼
VectorSearchService.findRelevantExamples(userQuery)
       │
       ▼
vectorStore.similaritySearch(userQuery, topK=5)
       │
   ┌───┴────┐
   │        │
   ▼        ▼
1. Embed  2. Search
   Query   2300 Vectors
   │        │
   └───┬────┘
       │
       ▼
3. Sort & Get Top 5
       │
       ▼
4. Format Results
       │
       ▼
Add to LLM Prompt
       │
       ▼
LLM generates better Elasticsearch query
       │
       ▼
Execute & Return Results
```

---

## 📊 Timeline

```
FIRST STARTUP (Lâu ~40 phút)
├─ T+0s:    App starts
├─ T+2s:    VectorStoreConfig created
├─ T+3s:    KnowledgeBaseIndexingService triggered
├─ T+10s:   Reading JSON files
├─ T+20s:   Creating documents
├─ T+30s:   Start embedding (OpenAI API calls)
├─ T+2400s: Finish embedding 2300 docs
├─ T+2410s: Save to vector_store.json
└─ T+2420s: ✅ Ready!

NEXT STARTUPS (Nhanh ~2 giây)
├─ T+0s:    App starts
├─ T+1s:    VectorStoreConfig created
├─ T+1.5s:  Load file vector_store.json
└─ T+2s:    ✅ Ready!

PER USER QUERY (Nhanh ~300ms)
├─ T+0ms:   Receive query
├─ T+100ms: Embed query (OpenAI)
├─ T+250ms: Calculate similarity
├─ T+300ms: Get top 5 + format
└─ T+300ms: Return to service
```

---

## 🔎 Similarity Search Detail

```
INPUT: "Show failed auth attempts"
       │
       ▼
STEP 1: Convert to Vector (1536 numbers)
       [-0.232, 0.893, -0.454, 0.122, ...]
       │
       ▼
STEP 2: Compare with All Documents
       │
   ┌───┴──────────────────────┐
   │                          │
   ▼                          ▼
Doc 1 Vector:            Doc 2 Vector:
[-0.234, 0.891, ...]     [-0.245, 0.885, ...]
Similarity: 0.987        Similarity: 0.985
   │                          │
   └───┬──────────────────────┘
       │
       ... (repeat for all 2300 documents)
       │
       ▼
STEP 3: Sort Results
       [0.987, 0.985, 0.750, 0.680, 0.670, ...]
       │
       ▼
STEP 4: Get Top 5
       [0.987 (Doc 1),
        0.985 (Doc 2),
        0.750 (Doc 3),
        0.680 (Doc 4),
        0.670 (Doc 5)]
       │
       ▼
OUTPUT: 5 Most Similar Documents
```

---

**Generated:** 2025-10-22
