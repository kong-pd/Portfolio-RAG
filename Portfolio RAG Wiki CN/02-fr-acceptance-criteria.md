# FR 验收标准（Acceptance Criteria）草案

> **项目**：Portfolio RAG 智能文档问答系统 · Wiki 首批页面 02
> **版本**：v0.1（草案，待评审） · **日期**：2026-06-13
> **配套**：表 3.1（FR 定义）、表 3.2（NFR）、3.5 用例规格、4.4 库表、表 4.10 端点
> **约定**：**P0** = 本周必须交付；**P1** = stretch。验收方式标记：`API`（curl / Postman 调接口）、`DB`（SQL 查验）、`UI`（手动操作）、`LOG`（日志查验）、`EVAL`（走评测集，见《03》）。

| 变更记录 | 日期 | 改动 | 原因 |
|---|---|---|---|
| v0.1 | 2026-06-13 | 初稿 | — |
| v0.2 | 2026-06-13 | 越权访问响应统一收口为 404 | 对齐《06》API 契约决策 D-03 |

---

## 通用 Definition of Done（每条 FR 关闭前逐项勾选）

- [ ] 该 FR 全部 P0 级 AC 通过，并留有自测记录（命令输出或截图）
- [ ] 接口与《API 契约》一致：路径、状态码、错误体 `{code, message, timestamp}`
- [ ] 异常路径有日志输出，不吞异常
- [ ] 敏感配置（API Key、数据库凭据）仅经环境变量注入，代码与配置文件零硬编码
- [ ] 代码符合 Controller / Service / Repository 分层
- [ ] 涉及分块、检索、Prompt 的改动，重跑评测冒烟子集（10 条）

---

## FR-01 用户认证（P0）

| # | Given | When | Then | 方式 |
|---|---|---|---|---|
| AC-01.1 | 访客 | `POST /api/auth/register` 提交合法邮箱 + ≥8 位密码 | 201 返回 `{userId}`；`users` 新增记录；库中 `password_hash` 为 BCrypt 哈希，绝非明文 | API+DB |
| AC-01.2 | 邮箱已注册 | 同邮箱再次注册 | 409 Conflict，提示更换邮箱 | API |
| AC-01.3 | 邮箱格式非法或密码 <8 位 | 提交注册 | 400，前端展示内联错误 | API+UI |
| AC-01.4 | 已注册用户 | `POST /api/auth/login` 正确凭据 | 200 返回 accessToken（15 min）+ refreshToken（7 d）；JWT 解码含 userId 与 exp；`refresh_tokens` 新增记录 | API+DB |
| AC-01.5 | 无 / 过期 / 伪造 Token | 请求任一受保护接口 | 401，错误体符合统一格式，不泄露内部细节 | API |
| AC-01.6 | accessToken 过期、refreshToken 有效 | 前端发起任意请求 | 拦截器经 `POST /api/auth/refresh` 静默换新并重放原请求，用户无感（UC-03 替代流 2a） | UI+LOG |
| AC-01.7 | 已登录用户 | `POST /api/auth/logout` | 204；对应 `refresh_tokens.revoked = true`；旧 refreshToken 再调 refresh → 401 | API+DB |

## FR-02 文档上传（P0）

| # | Given | When | Then | 方式 |
|---|---|---|---|---|
| AC-02.1 | 已登录 | `POST /api/documents/upload` 上传 8 MB 合法 PDF | 201 即时返回 `{documentId, status}`，**不等待嵌入完成**；`documents.status` 依次 pending → processing → done | API+DB |
| AC-02.2 | 已登录 | 上传 ≤5 MB 文档 | 全流程 ≤10 s 达到 done（NFR 性能） | DB |
| AC-02.3 | 已登录 | 上传 .docx / .exe / 改后缀伪装的文件 | 415（前端按扩展名拦截 + 后端按 MIME 兜底，两层独立验证） | API+UI |
| AC-02.4 | 已登录 | 上传 >20 MB 文件 | 前端拦截并提示；绕过前端直发 → 后端 413 | API+UI |
| AC-02.5 | 文档 done | 抽查 `document_chunks` | 每块 `token_count` ≤512；相邻块重叠 ≈64 token；`documents.chunk_count` 与实际块数一致；PDF 块带 `page_num` | DB |
| AC-02.6 | Embedding API 不可用（mock 故障） | 上传文档 | `status = error` 且 `error_msg` 记录原因；提供手动重试入口；恢复后重试 → done（UC-02 替代流 5a） | API+DB |
| AC-02.7 | 用户 B | 用 A 的 documentId 查详情 / 触发重试 | 404（防枚举，见《06》D-03），不泄露资源存在性 | API |

## FR-03 向量化存储（P0）

| # | Given | When | Then | 方式 |
|---|---|---|---|---|
| AC-03.1 | 文档 done | 查 `document_chunks` | 每块 `embedding` 为 1536 维向量；`document_id`、`user_id`（冗余过滤字段）、`chunk_index`、`content` 齐备 | DB |
| AC-03.2 | 任意查询向量 | 执行余弦相似度 top-5 SQL（走 IVFFlat 索引） | 返回 5 条且按相似度降序；`EXPLAIN` 确认命中向量索引 | DB |
| AC-03.3 | 连续上传多份文件 | 观察上传接口 | 嵌入任务走异步队列，接口响应不被阻塞（NFR 并发） | API+LOG |

## FR-04 RAG 问答（P0）

| # | Given | When | Then | 方式 |
|---|---|---|---|---|
| AC-04.1 | 已有 done 文档，问题可命中 | 提问 | 回答内容基于检索块；`messages.sources`（JSONB）写入 chunk_id / document_id / score / page_num；前端展示文档名 + 相似度 | UI+DB+EVAL |
| AC-04.2 | 所有块相似度 <0.75 | 提问知识库范围外的问题 | 返回「知识库中未找到相关内容」类拒答话术，零编造（UC-03 替代流 3a） | EVAL |
| AC-04.3 | 命中充分 | 查后端日志 | 注入 Prompt 的块数 ≤5（k=5） | LOG |
| AC-04.4 | LLM API 正常 | 连续 10 次提问 | 首 token 延迟 P50 ≤3 s（NFR） | LOG |
| AC-04.5 | 回答完成 | 查 `messages` | user 与 assistant 两条消息均落库，含 `token_usage` | DB |
| AC-04.6 | 用户 A、B 各有内容相近的语料 | B 提问仅 A 文档能答的问题 | **绝不命中 A 的块** → 触发拒答；检索 SQL 必含 `user_id` 过滤（安全红线，构造重叠语料专项验证） | DB+EVAL |

## FR-05 SSE 流式输出（P1）

> 前置决策：采纳 fetch + ReadableStream（或 fetch-event-source）方案以支持 Bearer 头，见《01》§2 G3。

| # | Given | When | Then | 方式 |
|---|---|---|---|---|
| AC-05.1 | 提问 | 观察前端 | 逐 token 增量渲染，非整段一次性出现 | UI |
| AC-05.2 | 生成结束 | 监听事件流 | 收到 `data: [DONE]` 后连接正常关闭，随后完整问答落库 | UI+DB |
| AC-05.3 | LLM 超时 / 异常（mock） | 提问 | 收到 `error` 事件；前端展示错误 + 重试按钮（UC-03 替代流 4a） | UI |
| AC-05.4 | 生成中 | 关闭页面 / 断网 | 后端检测断连并终止生成，无僵尸连接、无多余 token 消耗 | LOG |

## FR-06 对话历史（P1）

| # | Given | When | Then | 方式 |
|---|---|---|---|---|
| AC-06.1 | 存在多个会话 | `GET /api/conversations` | 分页正确，按 `updated_at` 倒序；新会话 `title` 默认取首问前 50 字 | API+UI |
| AC-06.2 | 进入某会话 | `GET /api/conversations/{id}/messages` | 消息按时序完整回放，含历史引用来源 | API+UI |
| AC-06.3 | 同一会话 ≥7 轮 | 发起第 8 轮提问 | Prompt 仅注入最近 6 轮（日志验证），更早轮次不注入 | LOG |
| AC-06.4 | 上一轮谈及某概念 | 用「它 / 这个」指代追问 | 回答正确解析指代（接评测集多轮型用例） | EVAL |
| AC-06.5 | 用户 B | 访问 A 的 conversationId | 404（防枚举，见《06》D-03） | API |

## FR-07 文档管理（P1）

| # | Given | When | Then | 方式 |
|---|---|---|---|---|
| AC-07.1 | 已上传多份文档 | `GET /api/documents?page=0&size=10` | 分页正确；状态徽标显示 pending / processing / done / error | API+UI |
| AC-07.2 | 删除一份文档 | `DELETE /api/documents/{id}` | 204；`documents` 与对应 `document_chunks` 级联清空（SQL count = 0）；随后提问不再命中该文档内容 | API+DB+EVAL |
| AC-07.3 | 用户 B | 携带 A 的文档 ID 调删除 | 404（防枚举，见《06》D-03），A 的数据完好 | API+DB |

---

## 统计与裁剪基线

P0 共 23 条 AC（FR-01~04），P1 共 12 条（FR-05~07）。触发《01》§3 熔断规则时，P1 整体延期，P0 的 23 条是本周不可让渡的底线。
