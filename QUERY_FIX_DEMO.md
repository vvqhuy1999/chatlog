# 🔧 **Demo: Sửa Lỗi Query Validation**

## 🚨 **Vấn đề đã được sửa:**

### **Lỗi 1: Bool must phải là array**
```json
// ❌ SAI (trước khi sửa):
{
  "query": {
    "bool": {
      "filter": [...],
      "must": {"exists": {"field": "fortinet.firewall.srcintfrole"}}  // ❌ Object thay vì array
    }
  }
}

// ✅ ĐÚNG (sau khi sửa):
{
  "query": {
    "bool": {
      "filter": [...],
      "must": [{"exists": {"field": "fortinet.firewall.srcintfrole"}}]  // ✅ Array
    }
  }
}
```

### **Lỗi 2: Aggs bị duplicate**
```json
// ❌ SAI (trước khi sửa):
{
  "query": {...},
  "aggs": {...},
  "aggs": {...}  // ❌ Duplicate aggs
}

// ✅ ĐÚNG (sau khi sửa):
{
  "query": {...},
  "aggs": {...}  // ✅ Chỉ có 1 aggs ở root level
}
```

## 🎯 **Câu hỏi test: "Kết nối nào có destination.bytes lớn hơn source.bytes (download > upload)?"**

### **Expected Query Structure:**
```json
{
  "query": {
    "bool": {
      "filter": [
        {
          "script": {
            "script": {
              "source": "doc['destination.bytes'].value > doc['source.bytes'].value"
            }
          }
        }
      ]
    }
  },
  "aggs": {
    "top_download_connections": {
      "terms": {
        "field": "source.ip",
        "size": 10
      },
      "aggs": {
        "total_download_bytes": {
          "sum": {
            "field": "destination.bytes"
          }
        },
        "total_upload_bytes": {
          "sum": {
            "field": "source.bytes"
          }
        },
        "download_upload_ratio": {
          "bucket_script": {
            "buckets_path": {
              "download": "total_download_bytes",
              "upload": "total_upload_bytes"
            },
            "script": "params.download / params.upload"
          }
        }
      }
    }
  },
  "size": 0
}
```

## 🔍 **Dynamic Examples sẽ tìm thấy:**

Từ knowledge base, hệ thống sẽ tìm thấy examples liên quan đến:
- **"bytes"** - destination.bytes, source.bytes
- **"download"** - download > upload
- **"kết nối"** - connections
- **"lớn hơn"** - comparison queries

## 📊 **Logging sẽ hiển thị:**

```
🔍 ===== QUERY MATCHING PROCESS =====
📝 User Query: "Kết nối nào có destination.bytes lớn hơn source.bytes (download > upload)?"
📚 Knowledge base contains 80 examples

🔍 Step 2 - Searching through knowledge base:
  📋 Example 65: Kết nối nào có destination.bytes lớn hơn source.bytes (downl...
     Keywords: destination.bytes, source.bytes, download, upload
     ✅ Match: 'destination.bytes' ↔ 'destination.bytes'
     ✅ Match: 'source.bytes' ↔ 'source.bytes'
     ✅ Match: 'download' ↔ 'download'
     ✅ Match: 'upload' ↔ 'upload'
     🎯 Total Score: 4 | Matched: destination.bytes, source.bytes, download, upload

🎯 Step 4 - Final Results (Top 5):
  1. Score: 4 | Kết nối nào có destination.bytes lớn hơn source.bytes (download > upload)?
     Keywords: destination.bytes, source.bytes, download, upload
```

## ✅ **Kết quả mong đợi:**

Sau khi sửa lỗi, hệ thống sẽ:
1. ✅ Tìm thấy example phù hợp từ knowledge base
2. ✅ Tạo query đúng cấu trúc (bool must là array)
3. ✅ Không bị duplicate aggs
4. ✅ Query validation thành công
5. ✅ Thực hiện tìm kiếm Elasticsearch thành công
6. ✅ Trả về kết quả so sánh giữa OpenAI và OpenRouter
