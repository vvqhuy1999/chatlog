
-- ============================================
-- 1. Kích hoạt pgvector extension
-- ============================================
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- 2. Tạo table với vector embedding
-- ============================================
CREATE TABLE IF NOT EXISTS public.ai_embedding (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    content text NOT NULL,
    metadata jsonb NULL,
    embedding vector(1536) NOT NULL,  -- 1536 dimensions cho OpenAI embeddings
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted integer NOT NULL DEFAULT 0,  -- 0 = not deleted, 1 = deleted (soft delete)
    CONSTRAINT ai_embedding_pkey PRIMARY KEY (id)
) TABLESPACE pg_default;

-- ============================================
-- 3. Tạo indexes để tối ưu tìm kiếm
-- ============================================
CREATE INDEX IF NOT EXISTS idx_ai_embedding_vector
    ON public.ai_embedding USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = '100') TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_ai_embedding_metadata
    ON public.ai_embedding USING gin (metadata) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_ai_embedding_created_at
    ON public.ai_embedding USING btree (created_at DESC) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_ai_embedding_is_deleted
    ON public.ai_embedding USING btree (is_deleted) TABLESPACE pg_default;

-- ============================================
-- 4. Enable RLS (Row Level Security) - Optional
-- ============================================
ALTER TABLE public.ai_embedding ENABLE ROW LEVEL SECURITY;

-- ============================================
-- 5. Kiểm tra table đã tạo
-- ============================================
SELECT * FROM public.ai_embedding LIMIT 1;
