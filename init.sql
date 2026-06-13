-- Portfolio RAG - PostgreSQL schema (pure DDL)
-- Requires the pgvector extension (image: pgvector/pgvector:pg16)

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- refresh_tokens (opaque UUID refresh tokens, 7-day TTL enforced in app)
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    token      UUID        NOT NULL UNIQUE,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------------
-- documents
-- ---------------------------------------------------------------------------
CREATE TABLE documents (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    filename    VARCHAR(512) NOT NULL,
    file_size   BIGINT       NOT NULL,
    mime_type   VARCHAR(128),
    status      VARCHAR(32)  NOT NULL DEFAULT 'pending',
    chunk_count INT          NOT NULL DEFAULT 0,
    error_msg   TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_user_id ON documents (user_id);

-- ---------------------------------------------------------------------------
-- document_chunks (1536-dim embeddings: text-embedding-3-small)
-- ---------------------------------------------------------------------------
CREATE TABLE document_chunks (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT       NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    content     TEXT         NOT NULL,
    embedding   vector(1536),
    chunk_index INT          NOT NULL,
    page_num    INT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_chunks_document_id ON document_chunks (document_id);
CREATE INDEX idx_document_chunks_user_id ON document_chunks (user_id);

-- IVFFlat index for cosine similarity search
CREATE INDEX idx_document_chunks_embedding
    ON document_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- ---------------------------------------------------------------------------
-- conversations
-- ---------------------------------------------------------------------------
CREATE TABLE conversations (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    title      VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_user_id ON conversations (user_id);

-- ---------------------------------------------------------------------------
-- messages
-- ---------------------------------------------------------------------------
CREATE TABLE messages (
    id                BIGSERIAL PRIMARY KEY,
    conversation_id   BIGINT      NOT NULL REFERENCES conversations (id),
    user_id           BIGINT      NOT NULL REFERENCES users (id),
    role              VARCHAR(16) NOT NULL,
    content           TEXT        NOT NULL,
    sources           JSONB,
    prompt_tokens     INT,
    completion_tokens INT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);
