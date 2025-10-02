# 🔍 Demo Chi Tiết: Quy Trình So Sánh và Chọn Query

Khi bạn chạy hệ thống với câu hỏi: **"Tìm các IP bị chặn nhiều nhất trong 24 giờ qua"**

## 📝 **Output từ hàm `buildDynamicExamples()`**

```
📝 ===== BUILDING DYNAMIC EXAMPLES =====
🔍 Finding relevant examples for: "Tìm các IP bị chặn nhiều nhất trong 24 giờ qua"

🔍 ===== QUERY MATCHING PROCESS =====
📝 User Query: "Tìm các IP bị chặn nhiều nhất trong 24 giờ qua"
📚 Knowledge base contains 100+ examples
🔤 Step 1 - Extracted keywords: [tìm, các, bị, chặn, nhiều, nhất, trong, giờ, qua]

🔍 Step 2 - Searching through knowledge base:
  📋 Example 1: Trong 24 giờ qua, những IP nguồn nào bị chặn (deny) nhiều nhất?...
     Keywords: IP nguồn, chặn, deny, nhiều nhất
     ✅ Match: 'ip' ↔ 'IP nguồn'
     ✅ Match: 'chặn' ↔ 'chặn'
     ✅ Match: 'nhiều' ↔ 'nhiều nhất'
     ✅ Match: 'nhất' ↔ 'nhiều nhất'
     🎯 Total Score: 4 | Matched: IP nguồn, chặn, nhiều nhất

  📋 Example 2: Có những lần đăng nhập thất bại của người dùng 'alice' trong 48 giờ qua không?...
     Keywords: đăng nhập, thất bại, người dùng, alice
     ❌ No matches found

  📋 Example 3: Trong 1 giờ qua, có lưu lượng RDP (port 3389) đi vào từ WAN không?...
     Keywords: RDP, port 3389, WAN, lưu lượng
     ❌ No matches found

  📋 Example 4: Những rule nào chặn nhiều nhất trong 24 giờ qua?...
     Keywords: rule, chặn, nhiều nhất
     ✅ Match: 'chặn' ↔ 'chặn'
     ✅ Match: 'nhiều' ↔ 'nhiều nhất'
     ✅ Match: 'nhất' ↔ 'nhiều nhất'
     🎯 Total Score: 3 | Matched: chặn, nhiều nhất

  📋 Example 5: Có dấu hiệu brute-force login (quá nhiều login failure từ 1 IP) trong 1 giờ qua không?...
     Keywords: brute-force, login, failure, IP
     ✅ Match: 'ip' ↔ 'IP'
     🎯 Total Score: 1 | Matched: IP

  📋 Example 6: User nào có tổng packets gửi đi (source.packets) nhiều nhất?...
     Keywords: user, tổng packets, gửi đi, nhiều nhất
     ✅ Match: 'nhiều' ↔ 'nhiều nhất'
     ✅ Match: 'nhất' ↔ 'nhiều nhất'
     🎯 Total Score: 2 | Matched: nhiều nhất

  📋 Example 7: Có user nào bị chặn khi cố gắng kết nối SSH?...
     Keywords: user, chặn, kết nối, SSH
     ✅ Match: 'chặn' ↔ 'chặn'
     🎯 Total Score: 1 | Matched: chặn

  ... (tiếp tục với tất cả examples)

📊 Step 3 - Sorting by relevance score:
  🔄 Comparing: Score 4 vs 3
  🔄 Comparing: Score 3 vs 2
  🔄 Comparing: Score 2 vs 1
  🔄 Comparing: Score 1 vs 1

🎯 Step 4 - Final Results (Top 5):
  1. Score: 4 | Trong 24 giờ qua, những IP nguồn nào bị chặn (deny) nhiều nhất?
     Keywords: IP nguồn, chặn, deny, nhiều nhất
  2. Score: 3 | Những rule nào chặn nhiều nhất trong 24 giờ qua?
     Keywords: rule, chặn, nhiều nhất
  3. Score: 2 | User nào có tổng packets gửi đi (source.packets) nhiều nhất?
     Keywords: user, tổng packets, gửi đi, nhiều nhất
  4. Score: 1 | Có dấu hiệu brute-force login (quá nhiều login failure từ 1 IP) trong 1 giờ qua không?
     Keywords: brute-force, login, failure, IP
  5. Score: 1 | Có user nào bị chặn khi cố gắng kết nối SSH?
     Keywords: user, chặn, kết nối, SSH

✅ Query matching process completed

🔨 Building dynamic examples string for AI prompt:
   - Found 5 relevant examples
   📄 Adding Example 1: Trong 24 giờ qua, những IP nguồn nào bị chặn (deny) nhiều nhất?...
      Keywords: IP nguồn, chặn, deny, nhiều nhất
   📄 Adding Example 2: Những rule nào chặn nhiều nhất trong 24 giờ qua?...
      Keywords: rule, chặn, nhiều nhất
   📄 Adding Example 3: User nào có tổng packets gửi đi (source.packets) nhiều nhất?...
      Keywords: user, tổng packets, gửi đi, nhiều nhất
   📄 Adding Example 4: Có dấu hiệu brute-force login (quá nhiều login failure từ 1 IP) trong 1 giờ qua không?...
      Keywords: brute-force, login, failure, IP
   📄 Adding Example 5: Có user nào bị chặn khi cố gắng kết nối SSH?...
      Keywords: user, chặn, kết nối, SSH
✅ Dynamic examples built successfully
📏 Total length: 2847 characters
📋 Preview (first 300 chars):
   RELEVANT EXAMPLES FROM KNOWLEDGE BASE:

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
...
🎯 ===== DYNAMIC EXAMPLES COMPLETED =====
```

## 🎯 **Giải Thích Chi Tiết Quy Trình So Sánh**

### **1. Trích xuất Keywords** 🔤
- **Input**: `"Tìm các IP bị chặn nhiều nhất trong 24 giờ qua"`
- **Process**: Tách thành từng từ, loại bỏ từ ngắn (< 3 ký tự)
- **Output**: `["tìm", "các", "bị", "chặn", "nhiều", "nhất", "trong", "giờ", "qua"]`

### **2. So sánh từng Example** 🔍
Với mỗi example trong knowledge base:

**Example 1: "Trong 24 giờ qua, những IP nguồn nào bị chặn (deny) nhiều nhất?"**
- Keywords: `["IP nguồn", "chặn", "deny", "nhiều nhất"]`
- So sánh:
  - `"ip"` ↔ `"IP nguồn"` ✅ (contains)
  - `"chặn"` ↔ `"chặn"` ✅ (exact match)
  - `"nhiều"` ↔ `"nhiều nhất"` ✅ (contains)
  - `"nhất"` ↔ `"nhiều nhất"` ✅ (contains)
- **Kết quả**: 4 matches → Score = 4

**Example 2: "Có những lần đăng nhập thất bại của người dùng 'alice' trong 48 giờ qua không?"**
- Keywords: `["đăng nhập", "thất bại", "người dùng", "alice"]`
- So sánh: Không có keyword nào match
- **Kết quả**: 0 matches → Score = 0 (bị loại bỏ)

### **3. Sắp xếp theo điểm** 📊
- Example 1: Score = 4 (cao nhất)
- Example 4: Score = 3
- Example 6: Score = 2
- Example 5: Score = 1
- Example 7: Score = 1

### **4. Xây dựng Dynamic Examples** 🔨
- Chọn top 5 examples có điểm cao nhất
- Xây dựng chuỗi context cho AI
- Bao gồm: Question, Keywords, và Elasticsearch Query

## ✨ **Lợi Ích của Logging Chi Tiết**

1. **Minh bạch**: Thấy rõ cách hệ thống so sánh
2. **Debug**: Dễ dàng tìm lỗi khi có vấn đề
3. **Hiểu biết**: Biết tại sao example nào được chọn
4. **Tối ưu**: Có thể điều chỉnh thuật toán matching
5. **Giám sát**: Theo dõi hiệu suất của hệ thống

Bây giờ khi bạn chạy hệ thống, console sẽ hiển thị đầy đủ quy trình so sánh để bạn hiểu rõ cách nó hoạt động!
