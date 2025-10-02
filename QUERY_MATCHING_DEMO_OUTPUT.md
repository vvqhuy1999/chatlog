# 🔍 Demo Output: Quy Trình So Sánh và Chọn Query

Khi người dùng nhập câu hỏi, hệ thống sẽ hiển thị chi tiết quy trình như sau:

## 📝 Ví dụ: "Tìm các IP bị chặn nhiều nhất trong 24 giờ qua"

```
🚀 ===== ELASTICSEARCH QUERY GENERATION PROCESS =====
📝 User Request: "Tìm các IP bị chặn nhiều nhất trong 24 giờ qua"
🆔 Session ID: 12345
📅 Date context generated for: 2025-01-27 14:30:15

🔍 Step 1: Finding relevant examples from knowledge base...

📝 ===== BUILDING DYNAMIC EXAMPLES =====

🔍 ===== QUERY MATCHING PROCESS =====
📝 User Query: "Tìm các IP bị chặn nhiều nhất trong 24 giờ qua"
📚 Knowledge base contains 100+ examples
🔤 Step 1 - Extracted keywords: [tìm, các, bị, chặn, nhiều, nhất, trong, giờ, qua]

🔍 Step 2 - Searching through knowledge base:
  ❌ Example 1: No matches
  ✅ Example 2: Score=3 | Matched keywords: IP nguồn, chặn, nhiều nhất
     Question: Trong 24 giờ qua, những IP nguồn nào bị chặn (deny) nhiều nhất?
  ❌ Example 3: No matches
  ✅ Example 4: Score=2 | Matched keywords: chặn, nhiều nhất
     Question: Những rule nào chặn nhiều nhất trong 24 giờ qua?
  ✅ Example 5: Score=1 | Matched keywords: chặn
     Question: Có user nào bị chặn khi cố gắng kết nối SSH?
  ❌ Example 6: No matches
  ... (tiếp tục với tất cả examples)

📊 Step 3 - Sorting by relevance score:
  Comparing: Score 3 vs 2
  Comparing: Score 2 vs 1

🎯 Step 4 - Final Results (Top 3):
  1. Score: 3 | Trong 24 giờ qua, những IP nguồn nào bị chặn (deny) nhiều nhất?
     Keywords: IP nguồn, chặn, deny, nhiều nhất
  2. Score: 2 | Những rule nào chặn nhiều nhất trong 24 giờ qua?
     Keywords: rule, chặn, nhiều nhất
  3. Score: 1 | Có user nào bị chặn khi cố gắng kết nối SSH?
     Keywords: user, chặn, kết nối, SSH

✅ Query matching process completed

🔨 Building dynamic examples string for AI prompt:
   - Found 3 relevant examples
   📄 Adding Example 1: Trong 24 giờ qua, những IP nguồn nào bị chặn (deny) nhiều nhất?...
   📄 Adding Example 2: Những rule nào chặn nhiều nhất trong 24 giờ qua?...
   📄 Adding Example 3: Có user nào bị chặn khi cố gắng kết nối SSH?...
✅ Dynamic examples built successfully
📏 Total length: 2847 characters
📋 Preview: RELEVANT EXAMPLES FROM KNOWLEDGE BASE:

Example 1:
Question: Trong 24 giờ qua, những IP nguồn nào bị chặn (deny) nhiều nhất?
Keywords: IP nguồn, chặn, deny, nhiều nhất
Query: {
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        { "term": { "fortinet.firewall.action": "deny" } },
        { "range": { "@timestamp": { "gte": "now-24h" } } }
      ]
    }
  },
  "aggs": {
    "top_blocked_sources": {
      "terms": { "field": "source.ip", "size": 50 }
    }
  }
}

Example 2:
Question: Những rule nào chặn nhiều nhất trong 24 giờ qua?
Keywords: rule, chặn, nhiều nhất
Query: {
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        { "term": { "fortinet.firewall.action": "deny" } },
        { "range": { "@timestamp": { "gte": "now-24h" } } }
      ]
    }
  },
  "aggs": {
    "rules": {
      "terms": { "field": "rule.name", "size": 50 }
    }
  }
}

Example 3:
Question: Có user nào bị chặn khi cố gắng kết nối SSH?
Keywords: user, chặn, kết nối, SSH
Query: {
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        { "term": { "destination.port": 22 } },
        { "term": { "fortinet.firewall.action": "deny" } },
        { "range": { "@timestamp": { "gte": "now-24h" } } }
      ]
    }
  },
  "aggs": {
    "blocked_users": {
      "terms": { "field": "source.user.name", "size": 50 }
    }
  }
}

🔨 Step 2: Building AI prompt with dynamic examples...
✅ Query prompt template created
📏 Prompt length: 15432 characters

📋 Step 3: Assembling complete prompt with:
   - System message (with dynamic examples)
   - Schema hints (15 schemas)
   - Sample log
   - User message
✅ Complete prompt assembled

🤖 Step 4: Calling AI to generate Elasticsearch query...
🆔 Conversation ID: 12345_query_generation
🌡️ Temperature: 0.0 (deterministic)
✅ AI query generation completed successfully
📄 Generated query body: {
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        { "term": { "fortinet.firewall.action": "deny" } },
        { "range": { "@timestamp": { "gte": "now-24h" } } }
      ]
    }
  },
  "aggs": {
    "top_blocked_sources": {
      "terms": { "field": "source.ip", "size": 50 }
    }
  }
}
🎯 ===== PROCESS COMPLETED =====
```

## 🎯 Tóm Tắt Quy Trình

### 1. **Trích xuất Keywords** 🔤
- Tách câu hỏi thành các từ riêng lẻ
- Loại bỏ từ ngắn (< 3 ký tự)
- Chuyển về chữ thường để so sánh

### 2. **Tìm kiếm trong Knowledge Base** 🔍
- Duyệt qua tất cả examples trong `fortigate_queries_full.json`
- So sánh keywords của user với keywords của từng example
- Tính điểm relevance dựa trên số lượng keyword matches

### 3. **Sắp xếp theo độ liên quan** 📊
- Sắp xếp examples theo điểm từ cao xuống thấp
- Chọn top 5 examples có điểm cao nhất
- Hiển thị chi tiết kết quả matching

### 4. **Xây dựng Dynamic Examples** 🔨
- Tạo chuỗi examples từ các kết quả đã chọn
- Bao gồm: question, keywords, và Elasticsearch query
- Đưa vào AI prompt để tạo context

### 5. **Gọi AI tạo Query** 🤖
- Sử dụng prompt với dynamic examples
- AI tạo ra Elasticsearch query phù hợp
- Trả về kết quả cuối cùng

## ✨ Lợi Ích Của Hệ Thống

- **Thông minh**: Chọn examples phù hợp với context
- **Minh bạch**: Hiển thị rõ ràng quy trình hoạt động
- **Hiệu quả**: Chỉ sử dụng examples liên quan
- **Dễ debug**: Có thể theo dõi từng bước
- **Linh hoạt**: Dễ dàng thêm/sửa examples trong JSON
