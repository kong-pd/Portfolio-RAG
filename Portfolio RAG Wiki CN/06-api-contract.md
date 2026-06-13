# API 契约（API Contract）

> **项目**：Portfolio RAG 智能文档问答系统 · Wiki 页面 06（G3 产出）
> **版本**：v0.1（草案，今日评审后冻结） · **日期**：2026-06-13 · **来源**：表 4.10 + 4.5.1，补全请求/响应示例与错误码
> **纪律**：本页冻结后即为前后端唯一事实源；任何改动走变更记录，并同步检查《02》对应 AC。

| 变更记录 | 日期 | 改动 | 原因 |
|---|---|---|---|
| v0.1 | 2026-06-13 | 初稿；含决策 D-01~D-05 | — |

---

## 1. 全局约定

- **基础路径** `/api`；版本经请求头 `API-Version: v1` 传递，不做路径版本化；
- **认证** 受保护端点须携带 `Authorization: Bearer <accessToken>`；
- **内容类型** 请求/响应默认 `application/json; charset=utf-8`；上传为 `multipart/form-data`；流式响应为 `text/event-stream`；
- **时间** 一律 ISO-8601 UTC（如 `2026-06-13T10:30:00Z`）；**ID** 一律 int64 数字；
- **分页** `page` 从 0 起，`size` 默认 10（文档）/ 20（会话），上限 50；响应统一用 `PageResponse` 包装（D-05）：

```json
{ "content": [], "page": 0, "size": 10, "totalElements": 0, "totalPages": 0 }
```

- **错误体** 全站统一（4.5.1）：

```json
{ "code": "EMAIL_ALREADY_EXISTS", "message": "该邮箱已被注册", "timestamp": "2026-06-13T10:30:00Z" }
```

## 2. 错误码字典

| HTTP | code | 场景 | 前端约定动作 |
|---|---|---|---|
| 400 | `VALIDATION_FAILED` | 参数缺失 / 邮箱格式 / 密码 <8 位 / 问题为空或 >2000 字 | 内联提示 |
| 401 | `BAD_CREDENTIALS` | 登录凭据错误 | 表单提示 |
| 401 | `TOKEN_EXPIRED` | Access Token 过期 | **拦截器静默刷新并重放原请求** |
| 401 | `UNAUTHORIZED` | Token 缺失 / 伪造 / 验签失败 | 跳登录页 |
| 401 | `REFRESH_TOKEN_INVALID` | Refresh 过期 / 已吊销 / 不存在 | 清本地状态，跳登录页 |
| 404 | `NOT_FOUND` | 资源不存在**或不属于当前用户**（D-03） | 通用提示 |
| 409 | `EMAIL_ALREADY_EXISTS` | 注册邮箱冲突 | 表单提示 |
| 413 | `PAYLOAD_TOO_LARGE` | 文件 >20 MB | 上传前置提示 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 非 PDF / TXT / MD | 上传前置提示 |
| 429 | `RATE_LIMITED` | post-MVP 预留（成本风险缓解） | 提示稍后再试 |
| 500 | `INTERNAL_ERROR` | 未分类服务器错误 | 通用错误提示 |
| 503 | `UPSTREAM_UNAVAILABLE` | 同步路径上外部 AI 服务不可用 | 提示稍后重试 |

> 401 细分为四个 code 是有意设计：前端拦截器**只在 `TOKEN_EXPIRED` 时刷新**，避免对伪造 Token 也发起刷新风暴；这直接支撑 AC-01.6。

## 3. 决策记录（契约级）

| # | 决策 | 理由 |
|---|---|---|
| D-01 | SSE 经 fetch + ReadableStream 携带 Bearer 头（不用原生 EventSource） | EventSource 不支持自定义请求头（《01》§2 已确认） |
| D-02 | 问答端点由 `GET /api/chat/stream?question=...` 改为 **POST + JSON body** | 采纳 D-01 后无 GET 约束；长问题不受 URL 长度限制；问题内容不进访问日志 |
| D-03 | 访问他人资源一律返回 **404**（而非 403） | 防资源枚举，「不泄露存在性」；《02》已同步收口（v0.2） |
| D-04 | MVP **不做 Refresh Token 轮换**，`/refresh` 仅返回新 accessToken | 与表 4.10 一致；轮换涉及并发竞态，列为 stretch（表 4.2 中 AuthService 的"轮换"职责相应降级） |
| D-05 | 分页统一 `PageResponse` 包装，不裸露 Spring `Page` 内部结构 | 防框架内部字段漂移进契约 |

---

## 4. 端点规格

### 4.1 认证组（无需 Bearer，logout 除外）

**POST /api/auth/register**

```http
POST /api/auth/register
API-Version: v1
Content-Type: application/json

{ "email": "ada@example.com", "password": "S3curePass!" }
```

`201` → `{ "userId": 1 }` ｜ 错误：400 `VALIDATION_FAILED`、409 `EMAIL_ALREADY_EXISTS`

**POST /api/auth/login**

请求体同上。`200` →

```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "refreshToken": "4f7c2a31-8c0e-4b5a-9f1d-2e6a7c3b9d10" }
```

> refreshToken 为不透明 UUID（4.7 安全设计），**不是 JWT**，服务端存于 `refresh_tokens` 表、可吊销。

错误：400、401 `BAD_CREDENTIALS`

**POST /api/auth/refresh**

请求 `{ "refreshToken": "4f7c…" }` → `200` `{ "accessToken": "eyJ…" }`（D-04：不轮换）｜ 错误：401 `REFRESH_TOKEN_INVALID`

**POST /api/auth/logout**（Bearer）

请求 `{ "refreshToken": "4f7c…" }` → `204`，服务端置 `revoked = true` ｜ 错误：401

### 4.2 文档组（Bearer）

**POST /api/documents/upload** — `multipart/form-data`，字段名固定为 `file`

`201` → `{ "documentId": 7, "status": "pending" }`（即时返回，不等嵌入）
错误：400（缺 file / 空文件）、413、415、401

**GET /api/documents?page=0&size=10**

`200` →

```json
{
  "content": [
    { "id": 7, "filename": "capstone-ch12.pdf", "fileSize": 2148033, "mimeType": "application/pdf",
      "status": "done", "chunkCount": 42, "errorMsg": null, "createdAt": "2026-06-13T09:00:00Z" },
    { "id": 8, "filename": "notes.md", "fileSize": 51200, "mimeType": "text/markdown",
      "status": "error", "chunkCount": 0, "errorMsg": "无法解析文件：Embedding 服务超时", "createdAt": "2026-06-13T09:05:00Z" }
  ],
  "page": 0, "size": 10, "totalElements": 2, "totalPages": 1
}
```

`status` 枚举：`pending | processing | done | error`

**DELETE /api/documents/{id}**

`204`，级联删除 `document_chunks`（AC-07.2）｜ 错误：401、404（含越权，D-03）

### 4.3 问答（Bearer）

**POST /api/chat/stream**（D-01 / D-02）

```json
{ "question": "文档分块用的是什么策略？", "conversationId": 42 }
```

- `conversationId` 可空：空 → 后端新建会话，并在首个 `meta` 事件返回新 id；
- 建连前 HTTP 错误：400（问题为空 / >2000 字）、401、404（conversationId 越权或不存在）。

**响应** `Content-Type: text/event-stream`，事件序列 `meta → sources → token* → [DONE]`：

```
data: {"type":"meta","conversationId":42}

data: {"type":"sources","items":[{"chunkId":311,"documentId":7,"filename":"capstone-ch34.pdf","score":0.87,"pageNum":12}]}

data: {"type":"token","content":"系统"}

data: {"type":"token","content":"采用"}

data: [DONE]
```

| 事件 | 说明 |
|---|---|
| `meta` | 永远是第一个事件；新会话在此拿到 conversationId |
| `sources` | 检索完成即发，先于生成（前端可立即渲染来源卡片）；`items: []` = 零命中，随后 token 流输出统一拒答话术（《07》F-04），**不算错误** |
| `token` | 逐 token 增量，`content` 为 JSON 转义文本（含换行安全） |
| `[DONE]` | 正常终止；此前 user / assistant 消息已落库 |
| `error` | 任意阶段可发，发出后连接关闭，**本轮 assistant 消息不落库**（《07》D-06）：`data: {"type":"error","code":"LLM_TIMEOUT","message":"模型响应超时，请重试"}` |

流内错误 code：`EMBEDDING_UNAVAILABLE` ｜ `LLM_TIMEOUT` ｜ `LLM_UPSTREAM_ERROR` ｜ `INTERNAL_ERROR`

### 4.4 会话组（Bearer）

**GET /api/conversations?page=0&size=20**

`200` → `PageResponse<ConversationDTO>`，按 `updatedAt` 倒序：

```json
{ "content": [ { "id": 42, "title": "文档分块用的是什么策略？", "createdAt": "2026-06-13T09:10:00Z", "updatedAt": "2026-06-13T09:18:00Z" } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
```

**GET /api/conversations/{id}/messages**

`200` →

```json
[
  { "id": 101, "role": "user", "content": "文档分块用的是什么策略？",
    "sources": null, "tokenUsage": null, "createdAt": "2026-06-13T09:10:02Z" },
  { "id": 102, "role": "assistant", "content": "系统采用固定大小分块……",
    "sources": [ { "chunkId": 311, "documentId": 7, "filename": "capstone-ch34.pdf", "score": 0.87, "pageNum": 12 } ],
    "tokenUsage": { "promptTokens": 812, "completionTokens": 156 }, "createdAt": "2026-06-13T09:10:09Z" }
]
```

错误：401、404（越权，D-03）

### 4.5 健康检查（公开白名单）

**GET /actuator/health** → `200` `{ "status": "UP" }`（compose 依赖与 D1 验收门均以此为准）

---

## 5. 契约冒烟

冻结当晚建一份 `api.http`（VS Code / IDEA REST Client 格式），按本页示例逐端点编排；D1 下午与 compose 骨架一起跑通，即为契约冒烟基线。后续每次后端合并前重放一遍，接口漂移当场暴露。
