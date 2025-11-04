-- ============================================
-- SQL QUERIES ĐỂ KIỂM TRA KEYWORDS TRONG SUPABASE
-- ============================================

-- 1. Xem cấu trúc metadata và keywords của một vài records mẫu
SELECT 
    id,
    content,
    metadata,
    metadata->'keywords' as keywords_raw,
    jsonb_typeof(metadata->'keywords') as keywords_type,
    metadata->>'question' as question
FROM ai_embedding 
WHERE is_deleted = 0 
LIMIT 5;

-- 2. Kiểm tra xem có bao nhiêu records có keywords là array
SELECT 
    jsonb_typeof(metadata->'keywords') as keywords_type,
    COUNT(*) as count
FROM ai_embedding 
WHERE is_deleted = 0
GROUP BY jsonb_typeof(metadata->'keywords');

-- 3. Xem chi tiết keywords array của một record cụ thể
SELECT 
    id,
    metadata->>'question' as question,
    metadata->'keywords' as keywords_array,
    jsonb_array_length(metadata->'keywords') as keywords_count,
    jsonb_array_elements_text(metadata->'keywords') as keyword_item
FROM ai_embedding 
WHERE is_deleted = 0 
  AND jsonb_typeof(metadata->'keywords') = 'array'
LIMIT 10;

-- 4. Test keyword matching với searchTerm = "truy cập"
-- (Giống như query trong hybrid search)
WITH test_search AS (
    SELECT 'truy cập ip'::text AS search_term
)
SELECT 
    a.id,
    a.metadata->>'question' as question,
    a.metadata->'keywords' as keywords_array,
    CASE 
        WHEN jsonb_typeof(a.metadata->'keywords') = 'array' AND EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(a.metadata->'keywords') AS keyword
            WHERE LOWER(keyword) LIKE LOWER(CONCAT('%', ts.search_term, '%'))
        ) THEN 0.9
        WHEN jsonb_typeof(a.metadata->'keywords') = 'array' AND EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(a.metadata->'keywords') AS keyword
            CROSS JOIN LATERAL unnest(string_to_array(ts.search_term, ' ')) AS word
            WHERE LOWER(keyword) LIKE LOWER(CONCAT('%', word, '%'))
        ) THEN 0.85
        WHEN LOWER(a.metadata->>'question') LIKE LOWER(CONCAT('%', ts.search_term, '%')) THEN 0.8
        ELSE 0.0
    END AS keyword_score
FROM ai_embedding a
CROSS JOIN test_search ts
WHERE a.is_deleted = 0
  AND jsonb_typeof(a.metadata->'keywords') = 'array'
ORDER BY keyword_score DESC
LIMIT 10;

-- 5. Xem các keywords phổ biến nhất
SELECT 
    keyword_item,
    COUNT(*) as frequency
FROM ai_embedding,
    LATERAL jsonb_array_elements_text(metadata->'keywords') AS keyword_item
WHERE is_deleted = 0
  AND jsonb_typeof(metadata->'keywords') = 'array'
GROUP BY keyword_item
ORDER BY frequency DESC
LIMIT 20;

-- 6. Kiểm tra records có keywords không phải array (có thể gây lỗi)
SELECT 
    id,
    metadata->>'question' as question,
    metadata->'keywords' as keywords_value,
    jsonb_typeof(metadata->'keywords') as keywords_type
FROM ai_embedding 
WHERE is_deleted = 0
  AND jsonb_typeof(metadata->'keywords') != 'array'
  AND metadata->'keywords' IS NOT NULL
LIMIT 10;

-- 7. Test với từng từ riêng lẻ (tách từ "truy cập ip")
SELECT 
    a.id,
    a.metadata->>'question' as question,
    a.metadata->'keywords' as keywords_array,
    word,
    keyword_item,
    CASE WHEN LOWER(keyword_item) LIKE LOWER(CONCAT('%', word, '%')) THEN 'MATCH' ELSE 'NO MATCH' END as match_status
FROM ai_embedding a
CROSS JOIN LATERAL unnest(string_to_array('truy cập ip', ' ')) AS word
CROSS JOIN LATERAL jsonb_array_elements_text(a.metadata->'keywords') AS keyword_item
WHERE a.is_deleted = 0
  AND jsonb_typeof(a.metadata->'keywords') = 'array'
LIMIT 20;


-- Bigrams: Dùng để tìm kiếm các cụm từ 2 từ liên tiếp
WITH terms AS (SELECT 'truy cập ip'::text AS s),
tokens AS (SELECT regexp_split_to_array(lower(s), '\s+') AS arr FROM terms)
SELECT a.id, a.metadata->>'question' AS question, k.keyword_item,
       (t.arr[i] || ' ' || t.arr[i+1]) AS gram,
       CASE WHEN lower(k.keyword_item) LIKE '%' || (t.arr[i] || ' ' || t.arr[i+1]) || '%'
            THEN 'MATCH' ELSE 'NO MATCH' END AS match_status
FROM ai_embedding a
CROSS JOIN LATERAL jsonb_array_elements_text(a.metadata->'keywords') AS k(keyword_item)
CROSS JOIN tokens t
CROSS JOIN generate_subscripts(t.arr, 1) AS g(i)
WHERE a.is_deleted = 0
  AND jsonb_typeof(a.metadata->'keywords') = 'array'
  AND g.i < cardinality(t.arr)
LIMIT 50;

-- fallback: Dùng để tìm kiếm các từ riêng lẻ
WITH words AS (
  SELECT unnest(string_to_array(lower('truy cập ip'), ' ')) AS w
)
SELECT a.id, a.metadata->>'question' AS question, k.keyword_item, w
FROM ai_embedding a
CROSS JOIN LATERAL jsonb_array_elements_text(a.metadata->'keywords') AS k(keyword_item)
CROSS JOIN words
WHERE a.is_deleted = 0
  AND jsonb_typeof(a.metadata->'keywords') = 'array'
  AND lower(k.keyword_item) LIKE '%' || w || '%'
LIMIT 50;


-- Đếm record có keyword chứa “website”:
SELECT count(*)
FROM ai_embedding
WHERE is_deleted = 0
  AND jsonb_typeof(metadata->'keywords') = 'array'
  AND EXISTS (
    SELECT 1
    FROM jsonb_array_elements_text(metadata->'keywords') kw
    WHERE lower(kw) LIKE '%website%'
  );

-- Xem nhanh 10 record khớp để đối chiếu:
SELECT id, metadata->>'question' AS question, metadata->'keywords' AS keywords
FROM ai_embedding
WHERE is_deleted = 0
  AND jsonb_typeof(metadata->'keywords') = 'array'
  AND EXISTS (
    SELECT 1
    FROM jsonb_array_elements_text(metadata->'keywords') kw
    WHERE lower(kw) LIKE '%website%'
  )
LIMIT 10;

-- Nếu muốn kiểm tra nhiều từ (vd: ‘truy’, ‘cập’, ‘website’):
WITH words AS (
  SELECT unnest(ARRAY['truy','cập','website']) AS w
)
SELECT count(*)
FROM ai_embedding a
WHERE is_deleted = 0
  AND jsonb_typeof(a.metadata->'keywords') = 'array'
  AND EXISTS (
    SELECT 1
    FROM jsonb_array_elements_text(a.metadata->'keywords') kw
    JOIN words ON lower(kw) LIKE '%' || words.w || '%'
  );