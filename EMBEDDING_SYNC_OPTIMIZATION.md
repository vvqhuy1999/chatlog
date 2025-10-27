# Tối Ưu Hóa Đồng Bộ Embeddings

## Vấn Đề Ban Đầu

Trước đây, mỗi lần khởi động ứng dụng, hệ thống sẽ:
1. Duyệt qua **TẤT CẢ** các entries trong file JSON
2. Với mỗi entry, thực hiện query kiểm tra trong database:
```sql
SELECT * FROM ai_embedding WHERE content = ? AND is_deleted = 0
```
3. Nếu đã tồn tại 184 embeddings, hệ thống vẫn phải chạy **184 queries** để kiểm tra

### Nhược Điểm
- ❌ Chậm: Phải query database 184 lần
- ❌ Tốn tài nguyên: Mỗi query phải scan database
- ❌ Không cần thiết: Khi dữ liệu đã đồng bộ, vẫn phải kiểm tra lại

## Giải Pháp Tối Ưu

### 1. So Sánh Count Trước Khi Xử Lý

Thay vì kiểm tra từng record, hệ thống sẽ:
1. Đếm số entries trong file JSON: `fileCount`
2. Đếm số embeddings trong database theo source file: `dbCount`
3. So sánh:
   - Nếu `fileCount == dbCount` → ✅ Bỏ qua file, không cần xử lý
   - Nếu `fileCount > dbCount` → 🆕 Chỉ xử lý `(fileCount - dbCount)` entries mới
   - Nếu `fileCount < dbCount` → ⚠️ Cảnh báo và tiếp tục xử lý

### 2. Query Tối Ưu

Thêm query đếm theo source file trong database:
```sql
SELECT COUNT(*) 
FROM ai_embedding 
WHERE metadata->>'source_file' = 'fortigate_queries_full.json' 
  AND is_deleted = 0
```

**Chỉ 1 query duy nhất** thay vì 184 queries!

## Kết Quả

### Trường Hợp 1: Dữ Liệu Đã Đồng Bộ (fileCount = dbCount)
```
🚀 Bắt đầu quá trình vector hóa kho tri thức và lưu vào Database...
📁 File: fortigate_queries_full.json
   📊 Số entries trong file: 184
   💾 Số embeddings trong DB: 184
   ✅ Dữ liệu đã đồng bộ, bỏ qua file này

📊 === KẾT QUẢ TỔNG HỢP ===
✅ Đã thêm 0 embeddings mới vào Database
📊 Tổng số embeddings hiện tại trong DB: 184
🎉 Hoàn thành quá trình đồng bộ!
```

**Số queries: 1** (thay vì 184)

### Trường Hợp 2: Có Dữ Liệu Mới (fileCount > dbCount)
```
🚀 Bắt đầu quá trình vector hóa kho tri thức và lưu vào Database...
📁 File: fortigate_queries_full.json
   📊 Số entries trong file: 200
   💾 Số embeddings trong DB: 184
   🆕 Phát hiện 16 entries mới cần thêm vào DB
   ✅ Đã xử lý 16 entries mới từ file fortigate_queries_full.json

📊 === KẾT QUẢ TỔNG HỢP ===
✅ Đã thêm 16 embeddings mới vào Database
📊 Tổng số embeddings hiện tại trong DB: 200
🎉 Hoàn thành quá trình đồng bộ!
```

**Số queries: 1 (count) + 16 (check duplicates) = 17** (thay vì 200)

### Trường Hợp 3: File Bị Xóa Bớt (fileCount < dbCount)
```
🚀 Bắt đầu quá trình vector hóa kho tri thức và lưu vào Database...
📁 File: fortigate_queries_full.json
   📊 Số entries trong file: 180
   💾 Số embeddings trong DB: 184
   ⚠️ Cảnh báo: DB có nhiều records hơn file (184 > 180)
   💡 Có thể file đã bị xóa bớt entries. Tiếp tục xử lý...
   ✅ Đã xử lý 0 entries mới từ file fortigate_queries_full.json

📊 === KẾT QUẢ TỔNG HỢP ===
✅ Đã thêm 0 embeddings mới vào Database
📊 Tổng số embeddings hiện tại trong DB: 184
🎉 Hoàn thành quá trình đồng bộ!
```

## Các Thay Đổi Code

### 1. Repository: `AiEmbeddingRepository.java`
```java
// Đếm số embeddings theo source file
@Query(nativeQuery = true, value = "SELECT COUNT(*) FROM ai_embedding a WHERE a.metadata->>'source_file' = ?1 AND a.is_deleted = 0")
long countBySourceFile(String sourceFile);
```

### 2. Service Interface: `AiEmbeddingService.java`
```java
// Đếm số embeddings theo source file
long countBySourceFile(String sourceFile);
```

### 3. Service Implementation: `AiEmbeddingServiceImpl.java`
```java
@Override
public long countBySourceFile(String sourceFile) {
    return aiEmbeddingRepository.countBySourceFile(sourceFile);
}
```

### 4. Indexing Service: `KnowledgeBaseIndexingService.java`
- Thêm logic so sánh count trước khi xử lý
- Thêm logging chi tiết cho từng trường hợp
- Bỏ qua file nếu dữ liệu đã đồng bộ

## Lợi Ích

1. **Hiệu Suất** 🚀
   - Giảm từ 184 queries xuống còn 1 query khi dữ liệu đã đồng bộ
   - Tăng tốc độ khởi động ứng dụng

2. **Tài Nguyên** 💾
   - Giảm tải cho database
   - Giảm network traffic

3. **Trải Nghiệm** ✨
   - Khởi động nhanh hơn
   - Log rõ ràng, dễ theo dõi

4. **Mở Rộng** 📈
   - Dễ dàng thêm nhiều file JSON khác
   - Logic tự động phát hiện và xử lý entries mới

## Cách Sử Dụng

Không cần thay đổi gì! Hệ thống tự động:
1. Kiểm tra count khi khởi động
2. Chỉ xử lý khi cần thiết
3. Log kết quả rõ ràng

## Lưu Ý

- Hệ thống vẫn kiểm tra duplicate cho từng entry mới (để đảm bảo không trùng lặp)
- Nếu file JSON được cập nhật, chỉ cần restart ứng dụng
- Database count dựa trên `source_file` trong metadata

