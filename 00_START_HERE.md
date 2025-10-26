# 🚀 Dual Database Chatlog System - START HERE

## What This Project Does

A Spring Boot application with **2 separate databases**:
- **Primary DB** (localhost): Stores chat messages & sessions
- **Secondary DB** (Supabase): Stores AI embeddings with vector search

---

## ⚡ Quick Start (5 Minutes)

### 1. Docker PostgreSQL
```bash
docker run -d --name postgres -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=chatlog -p 5432:5432 postgres:15
```

### 2. Supabase Setup
- Visit: https://app.supabase.com
- Go to **SQL Editor** and run `embedding.sql`

### 3. Environment Variables
```powershell
$env:POSTGRES_USER = "postgres"
$env:POSTGRES_PASSWORD = "postgres"
$env:SECONDARY_DATASOURCE_USERNAME = "postgres.wdxshprlefoixyyuxcwl"
$env:SECONDARY_DATASOURCE_PASSWORD = "your_password"
```

### 4. Build & Run
```bash
mvn clean install
mvn spring-boot:run
```

### 5. Test
```bash
curl http://localhost:8080/test/connections
```

---

## 📚 Documentation

| File | Purpose |
|------|---------|
| **FINAL_SETUP_GUIDE.md** | Complete setup with troubleshooting |
| **embedding.sql** | Database schema for Supabase |
| **application.yaml** | Configuration with env vars |
| **RUN_COMMANDS.md** | Common commands reference |

---

## 🏗️ Architecture

```
┌─ Chat API ─────────────────────────────────────────────┐
│                                                         │
│  ├─ ChatMessagesController                            │
│  ├─ ChatSessionsController                            │
│  └─ AuthController                                    │
│                                                         │
└────────────────────┬────────────────────┬─────────────┘
                     │                    │
        ┌────────────┴──────┐   ┌────────┴─────────┐
        │                   │   │                  │
   PRIMARY DB          SECONDARY DB          VECTOR STORE
   (localhost)         (Supabase)            (In-Memory)
        │                   │                       │
   ├─ Chats            ├─ Embeddings           Fallback
   └─ Sessions         └─ Vectors              Cache
```

---

## 📂 Project Structure

```
src/main/java/com/example/chatlog/
├── config/
│   ├── PrimaryDataSourceConfig.java      ← Chat DB config
│   ├── SecondaryDataSourceConfig.java    ← Embedding DB config
│   └── VectorStoreConfig.java
│
├── entity/
│   ├── ChatMessages.java                 → Primary DB
│   ├── ChatSessions.java                 → Primary DB
│   └── AiEmbedding.java                  → Secondary DB
│
├── repository/
│   ├── ChatMessagesRepository            → Primary DB
│   ├── ChatSessionsRepository            → Primary DB
│   └── AiEmbeddingRepository             → Secondary DB (Native SQL)
│
├── service/
│   ├── ChatMessagesService
│   ├── ChatSessionsService
│   ├── AiEmbeddingService                ← Vector conversion
│   └── impl/
│       ├── AiEmbeddingServiceImpl
│       ├── KnowledgeBaseIndexingService  ← Auto-index on startup
│       └── VectorSearchService           ← Search from DB
│
└── controller/
    ├── ChatMessagesController
    ├── ChatSessionsController
    └── AuthController
```

---

## 🔑 Key Features

✅ **Dual Database Separation**
- Chat operations on local PostgreSQL
- Embeddings on Supabase with pgvector

✅ **Automatic Indexing**
- Knowledge base auto-vectorizes on startup
- 2300+ examples indexed

✅ **Vector Search**
- Native PostgreSQL IVFFLAT indexing
- Cosine distance similarity

✅ **Soft Deletes**
- `is_deleted` column (0=active, 1=deleted)
- No data loss

✅ **Type Safety**
- Java entities map to database schemas
- Automatic DDL management

---

## 🧪 Test Endpoints

```bash
# Health check
curl http://localhost:8080/actuator/health

# Test both datasources
curl http://localhost:8080/test/connections

# Create chat session
curl -X POST http://localhost:8080/api/chat/sessions \
  -H "Content-Type: application/json" \
  -d '{"userId":"user123"}'

# Get embeddings
curl http://localhost:8080/api/embeddings/all
```

---

## 🐛 Common Issues & Fixes

| Error | Fix |
|-------|-----|
| `vector type does not exist` | Run `embedding.sql` on Supabase |
| `Cannot connect to Supabase` | Check password, verify SSL |
| `Port 8080 in use` | `netstat -ano \| findstr :8080` |
| `No tables in local DB` | App auto-creates them |

---

## 🚀 Production Deployment

```bash
# Build JAR
mvn clean package -DskipTests

# Run with env vars
java -jar target/chatlog-0.0.1-SNAPSHOT.jar \
  -DPOSTGRES_USER=postgres \
  -DPOSTGRES_PASSWORD=secure_pass \
  -DSECONDARY_DATASOURCE_USERNAME=... \
  -DSECONDARY_DATASOURCE_PASSWORD=...
```

---

## 📊 Database Details

### Primary Database (localhost:5432)
```
Database: chatlog
Tables:
- chat_messages
- chat_sessions
- spring_session (auto)
- spring_session_attributes (auto)
```

### Secondary Database (Supabase)
```
Database: postgres
Tables:
- ai_embedding (with vector support)
Indexes:
- IVFFLAT (vector similarity)
- GIN (JSON metadata)
- BTREE (soft delete)
```

---

## 📞 Need Help?

1. **See Full Guide**: Read `FINAL_SETUP_GUIDE.md`
2. **Run Commands**: Check `RUN_COMMANDS.md`
3. **Check Logs**: Application logs show detailed info
4. **Verify DB**: Test connections endpoint

---

## ✨ What's Next?

- [ ] Run application
- [ ] Verify both databases working
- [ ] Test chat functionality
- [ ] Test vector search
- [ ] Deploy to production

---

**Status**: ✅ Ready to Deploy!  
**Version**: 1.0  
**Last Updated**: 2025-10-26
