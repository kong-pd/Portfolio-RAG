# Portfolio RAG

A full-stack RAG (Retrieval-Augmented Generation) document Q&A system. Upload PDF, TXT, or Markdown files and ask questions against them with accurate, source-cited answers — no hallucination on out-of-scope topics.

Built as a portfolio project demonstrating production-grade Spring Boot + pgvector + multi-provider AI integration.

---

## Architecture

```
Browser (React + Vite)
        │  REST / JSON
        ▼
Spring Boot 3 (port 8080)
  ├─ Auth     JWT access (15 min) + opaque refresh (7 d)
  ├─ Document upload → async chunk + embed
  ├─ RAG      embed query → HNSW cosine search → LLM prompt
  └─ Chat     multi-provider router (Groq → OpenAI fallback)
        │
        ├─ PostgreSQL 16 + pgvector  (documents, chunks, conversations)
        └─ OpenAI-compatible API     (Groq / OpenAI / Gemini)
```

---

## Features

| Area | Detail |
|---|---|
| **Auth** | Register / login / refresh / logout; BCrypt passwords; JWT dual-token; per-user data isolation |
| **Ingestion** | PDF (page-aware), TXT, Markdown; async status: `pending → processing → done / error`; manual retry |
| **Chunking** | Real token sliding window via **jtokkit** cl100k\_base (512 tokens, 64 overlap) |
| **Embeddings** | `text-embedding-3-small` (1 536-dim) via OpenAI; Gemini 768-dim supported with schema migration |
| **Search** | pgvector HNSW cosine index; k = 5; cosine threshold 0.75; hard `user_id` filter (no cross-user leakage) |
| **RAG answer** | Injects top-k chunks + last 6 conversation turns; below-threshold → explicit refusal, no hallucination |
| **AI routing** | Configurable provider list (`groq`, `openai`, `gemini`); runtime fallback — first success wins |
| **Token tracking** | `prompt_tokens` / `completion_tokens` persisted per assistant message |
| **Deployment** | Docker Compose one-command start; `restart: unless-stopped` on all services |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring AI 1.0, Spring Security, Spring Data JPA |
| AI | Spring AI `ChatClient` / `EmbeddingModel`; OpenAI-compatible endpoint for Groq & Gemini |
| Database | PostgreSQL 16 + pgvector extension; HNSW index |
| PDF parsing | Apache PDFBox 3 |
| Token counting | jtokkit 1.1 (cl100k\_base) |
| JWT | jjwt 0.12 |
| Frontend | React 18, Vite 5 (scaffold — full UI in progress) |
| Container | Docker Compose 3, pgvector/pgvector:pg16 image |

---

## Prerequisites

- Docker & Docker Compose
- At least **one** AI provider API key:
  - [Groq](https://console.groq.com) — free, recommended for prototyping
  - [OpenAI](https://platform.openai.com) — required for embeddings unless switched to Gemini
  - [Google AI Studio](https://aistudio.google.com) — optional Gemini fallback

---

## Quick Start

```bash
# 1. Clone
git clone https://github.com/kong-pd/Portfolio-RAG.git
cd Portfolio-RAG

# 2. Configure environment
cp .env.example .env
# Edit .env — set at minimum GROQ_API_KEY and OPENAI_API_KEY

# 3. Start all services
docker compose up --build -d

# Backend:  http://localhost:8080
# Frontend: http://localhost:3000
# Health:   http://localhost:8080/actuator/health
```

The database schema is applied automatically from `init.sql` on first start.

---

## AI Provider Configuration

Edit `backend/src/main/resources/application.yml` (or override via env vars):

```yaml
app:
  ai:
    chat:
      providers:          # tried in order at runtime — first success wins
        - groq
        - openai          # fallback
        # - gemini        # uncomment + set GOOGLE_API_KEY
    embedding:
      provider: openai    # openai (1536-dim) | gemini (768-dim, needs schema migration)
      dimensions: 1536
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat-model: gpt-4o
      embedding-model: text-embedding-3-small
    groq:
      api-key: ${GROQ_API_KEY:}
      chat-model: llama-3.3-70b-versatile
      base-url: https://api.groq.com/openai
    gemini:
      api-key: ${GOOGLE_API_KEY:}
      base-url: https://generativelanguage.googleapis.com/v1beta/openai
      chat-model: gemini-2.0-flash
```

**Switching embedding providers** changes vector dimensions and requires:
1. Alter `document_chunks.embedding` column type (e.g. `vector(768)`)
2. Update `app.ai.embedding.dimensions` to match
3. Re-upload all documents to re-embed

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `GROQ_API_KEY` | One of these | Chat LLM — free tier at console.groq.com |
| `OPENAI_API_KEY` | One of these + embeddings | Chat fallback + `text-embedding-3-small` |
| `GOOGLE_API_KEY` | Optional | Gemini chat / embedding via OpenAI-compat endpoint |
| `POSTGRES_USER` | Yes | DB username |
| `POSTGRES_PASSWORD` | Yes | DB password |
| `POSTGRES_DB` | Yes | DB name |
| `JWT_SECRET` | Yes | ≥ 256-bit secret for HS256 signing |

---

## API Overview

All endpoints under `/api`. Protected routes require `Authorization: Bearer <accessToken>`.

### Auth
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create account → `{userId}` |
| `POST` | `/api/auth/login` | Credentials → `{accessToken, refreshToken}` |
| `POST` | `/api/auth/refresh` | Rotate token pair → `{accessToken, refreshToken}` |
| `POST` | `/api/auth/logout` | Revoke refresh token |

### Documents (auth required)
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/documents/upload` | Upload file (multipart), async ingestion |
| `GET` | `/api/documents` | Paginated list with status badges |
| `POST` | `/api/documents/{id}/retry` | Retry failed ingestion |
| `DELETE` | `/api/documents/{id}` | Delete document + all chunks |

### Chat (auth required)
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/chat/stream` | Ask question → `{conversationId, answer, sources}` |
| `GET` | `/api/conversations` | Paginated conversation list |
| `GET` | `/api/conversations/{id}/messages` | Full message history |

**Response shape for errors:**
```json
{ "code": "ERROR_CODE", "message": "Human-readable detail", "timestamp": "..." }
```

---

## Local Development

### Backend

Requirements: Java 21, Maven 3.9+, PostgreSQL with pgvector

```bash
# Start only the database
docker compose up postgres -d

# Run backend locally
cd backend
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-DPOSTGRES_USER=raguser -DPOSTGRES_PASSWORD=changeme -DPOSTGRES_DB=ragdb -DDB_HOST=localhost -DOPENAI_API_KEY=sk-..."
```

### Frontend

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173  (proxies /api → localhost:8080)
```

---

## Running Tests

```bash
# Unit + slice tests (no DB required)
cd backend
mvn test

# Or via Docker if local Java 21 is unavailable
docker run --rm \
  -v "$(pwd)/backend":/app \
  -w /app \
  maven:3.9-eclipse-temurin-21 \
  mvn test -q
```

Test coverage:
- `AuthControllerTest` / `AuthServiceTest` / `JwtServiceTest` — auth slice
- `ChatControllerTest` — RAG endpoint (mocked RagService)
- `DocumentControllerTest` / `DocumentServiceTest` — document management
- `TextChunkingTest` — jtokkit token-window chunking correctness
- `GlobalExceptionHandlerTest` — error response shape

---

## Project Structure

```
Portfolio-RAG/
├── backend/                        Spring Boot application
│   ├── src/main/java/com/portfolio/rag/
│   │   ├── ai/          ChatRouter  (multi-provider fallback)
│   │   ├── auth/        JWT auth, register/login/refresh/logout
│   │   ├── chat/        RagService, ChatController, conversation entities
│   │   ├── common/      Error handling, pagination, security utils
│   │   ├── config/      AppProperties, SecurityConfig, AiRoutingConfig
│   │   └── document/    Upload, async ingestion, jtokkit chunking
│   └── src/main/resources/
│       └── application.yml
├── frontend/                       React 18 + Vite (UI in progress)
├── Portfolio RAG Wiki CN/          Design docs (CN): scope, FR ACs, API contract, arch
├── docker-compose.yml
├── init.sql                        PostgreSQL schema (pgvector + HNSW index)
└── .env.example
```

---

## Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Embedding dimensions | 1 536 (OpenAI) | Matches `text-embedding-3-small`; Gemini 768 supported via config + schema migration |
| Vector index | HNSW (`m=16, ef_construction=64`) | No training step, works on empty table, better recall than IVFFlat at this scale |
| Chunking | jtokkit cl100k\_base, 512 tok / 64 overlap | Real token count avoids over/under-chunking on CJK + code |
| AI routing | Provider list + runtime fallback | Groq for speed/cost in dev; OpenAI as production fallback; zero code change to switch |
| Refresh token | Opaque UUID in DB (revocable) | Enables instant logout; avoids JWT refresh-token anti-pattern |
| Transaction split in RagService | Short TX A + no-TX LLM call + short TX B | Prevents Hikari pool exhaustion during 10–60 s LLM calls under concurrent load |

---

## Roadmap

- [ ] React frontend: auth, document manager, chat UI, source citations
- [ ] SSE streaming output (FR-05)
- [ ] Conversation list + history replay (FR-06)
- [ ] RAG evaluation golden set (28 questions)
- [ ] Gemini embedding support (768-dim schema migration path)
