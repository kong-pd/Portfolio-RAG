# 02 · 需求与验收标准（Requirements）

> **用途**：本文件是全项目唯一的验收来源。`04-TASKPLAN` 里每张任务卡的验收清单都必须引用这里的 FR 编号，不得私自新增或放宽。NFR 一节同时是面试里"非功能性你考虑了什么"的答案库。
> **状态**：v1.0 定稿（2026-07-10）
> **覆盖范围**：P0 需求带完整验收标准；P1/P2 只列条目，进入 Gate 3 前再补验收（把判断力预算花在刀刃上）。

## 0. 验收总则

1. 每条验收标准必须**可机器验证**（测试名、状态码、命令输出）或**可走查打勾**（明确的手工步骤 + 预期现象）。"正常工作""体验良好"之类措辞禁止出现。
2. FR 未列出的行为默认不承诺；想承诺，先改本文件。
3. 编号规则：`FR-<模块><序号>`。模块：A 认证与隔离 / D 文档 / Q 问答 / E 工程底座 / U 前端。

## 1. User Stories（4 条，定基调用）

- **US-1**：作为备战面试的求职者，我上传目标公司的年报，提问"2025 年净利润和主要风险是什么"，得到附来源片段的回答，快速完成公司调研。
- **US-2**：作为小团队成员，我把内部合规手册放进自己的知识库，用自然语言查条款，不必翻全文。
- **US-3**：作为在意隐私的用户，我确信我的文档和问答只有我自己可见，其他用户无论如何访问不到。
- **US-4**：作为评估这个项目的面试官，我 clone 仓库后一条命令跑起 demo，十分钟内看懂架构与取舍。

## 2. 功能需求（P0，含验收标准）

### A · 认证与隔离

**FR-A1 注册**：邮箱 + 密码注册；密码 ≥ 8 位；BCrypt 存储。
- [ ] `POST /api/auth/register` 合法请求 → 201，响应含用户 id，不含密码或 hash
- [ ] 重复邮箱 → 409；非法邮箱格式或弱密码 → 400，错误信息精确到字段
- [ ] 数据库 password 列为 BCrypt hash（`$2` 开头），无明文

**FR-A2 登录**：返回短效 access token（15 分钟）+ refresh token（7 天）。
- [ ] 正确凭证 → 200，含两个 token；access token payload 含 sub 与 exp
- [ ] 错误凭证 → 401，且"用户不存在"与"密码错误"返回**同一段文案**（防账号枚举）

**FR-A3 刷新与登出**：`POST /api/auth/refresh` 以 refresh token 换新 access token；`POST /api/auth/logout` 吊销当前 refresh token（refresh token 落库，见 ADR-004）。
- [ ] 有效 refresh → 200 含新 access；过期或伪造 → 401
- [ ] 登出后原 refresh token 再用 → 401（吊销生效）

**FR-A4 全局鉴权**：除 auth 端点与健康检查外，一切端点要求 Bearer token。
- [ ] 缺失 / 无效 / 过期 token → 401，响应体为 Problem Details 格式（见 FR-E4）

**FR-A5 数据隔离（本项目的命门）**：文档、会话、消息、**向量检索**全部按 user_id 隔离。
- [ ] `DocumentIsolationTest` 全绿（Testcontainers 起真实 Postgres；Phase 2 落地）
- [ ] `ConversationIsolationTest` 全绿（会话功能就位后落地，Phase 4）
- [ ] 用户 A 以合法 token 访问 B 的具体资源 → **404**（而非 403，不泄露资源存在性，见 ADR-004）
- [ ] A 的所有列表接口结果中 B 的记录为零条
- [ ] 检索层用例：在 B 的文档里埋一段独有内容，A 提问该内容 → 检索不命中、回答拒答（证明 metadata filter 生效）

### D · 文档

**FR-D1 上传**：multipart 上传 PDF / TXT / Markdown，单文件 ≤ 20 MB。
- [ ] 合法上传 → 201，返回 document id 与 status=UPLOADED
- [ ] 超过 20 MB → 413；扩展名或 MIME 不在白名单 → 415
- [ ] 文件名经清洗后存储（路径穿越字符被剥离，见 NFR-1）

**FR-D2 配额**：单用户至多 50 份文档。
- [ ] 第 51 份上传 → 409，文案明确提示配额与当前用量

**FR-D3 异步解析与向量化**：状态机 UPLOADED → PROCESSING → READY / FAILED。
- [ ] 上传接口立即返回，不等待解析（解析经 @Async 派发到独立线程池 `ingestionExecutor`，见 03 §9.3）
- [ ] 正常文档在合理时间内变 READY，记录 chunk 数；DB 的 chunks 表每行 embedding 非空
- [ ] 故意上传损坏 PDF → 该文档 FAILED 并记录用户可读的失败原因；**不影响**其他文档与问答服务

**FR-D4 列表与详情**：分页列表（默认每页 20），含文件名、状态、大小、chunk 数、创建时间。
- [ ] 分页参数生效；越界页返回空列表而非错误

**FR-D5 删除**：删除文档时级联清理向量与原文件。
- [ ] 删除后该文档 chunks 计数为 0，磁盘 / S3 上原文件消失
- [ ] 删除后针对该文档独有内容提问 → 检索不再命中（问答验证）
- [ ] 再次 GET 该文档 → 404

**FR-D6 中英文内容**：中文与英文文档均可正确抽取、切块、检索。
- [ ] 各上传一份中文、英文样例 PDF，分别提问各自内容，均答对且引用正确
- [ ] 返回的中文 chunk 文本无乱码、无半字截断

### Q · 问答

**FR-Q1 提问**：`POST /api/conversations/{id}/messages`，body 为 `{content}`；top-k 向量检索（默认 k=4，配置化）。
- [ ] k 值可经 application.yml 调整且生效（测试验证）

**FR-Q2 来源引用（差异化核心，不可砍）**：回答附 `sources: [{documentId, filename, chunkId, snippet, score}]`。
- [ ] 每次基于知识库的回答 sources 非空
- [ ] snippet 与 DB 中对应 chunk 文本一致（抽查用例）

**FR-Q3 SSE 流式**：`text/event-stream`，事件序列 `token`（多次）→ `sources` → `done`；异常时发 `error` 事件后关闭。
- [ ] `curl -N` 可观察到逐 token 输出，事件类型与顺序符合协议
- [ ] 上游模型报错时客户端收到 error 事件，连接干净关闭，服务端无未捕获异常堆栈

**FR-Q4 拒答**：检索结果为空或最高相似度低于阈值时，明确回答"资料中没有找到相关内容"，不编造。
- [ ] 3 条精心构造的库外问题用例全部拒答
- [ ] 拒答时 sources 为空数组

**FR-Q5 多轮**：会话内携带近 N 轮上下文（默认 N=6 条消息，配置化），支持指代。
- [ ] 两轮指代用例通过（例：先问"X 公司 2025 营收"，再问"那它的净利润呢"）

**FR-Q6 会话管理**：新建 / 列表（按最近活动排序）/ 删除（级联删消息）/ 拉取历史消息。
- [ ] 删除会话后其消息在 DB 中为零条；列表排序正确

### E · 工程底座

**FR-E1 一键启动**：`docker compose up --build` 起全套（后端 + Postgres/pgvector）。
- [ ] 在一台干净机器（或新虚拟机）上按 README 步骤走通全流程，一次成功

**FR-E2 API 文档**：springdoc 生成 `/swagger-ui`；Postman collection 随仓库提交。
- [ ] Swagger 页面可访问，端点与实现一致；collection 覆盖全部 P0 端点且全绿

**FR-E3 限流韧性**：对上游 429 做指数退避重试（上限次数配置化）；ingestion 分批调用 embedding。
- [ ] 模拟 429（mock 或压低配额）下，任务最终成功或优雅转 FAILED，服务不崩
- [ ] 日志可见退避轨迹（间隔递增）

**FR-E4 统一错误格式**：全部 4xx/5xx 采用 RFC 9457 Problem Details。
- [ ] 任一错误响应含 type/title/status/detail 字段；生产 profile 下不泄漏堆栈

**FR-E5 密钥外置**：所有密钥走环境变量。
- [ ] repo 与 git 历史无真实 key；`.env` 在 .gitignore；`.env.example` 字段完整可照抄

### U · 前端（Gate 2；验收 = 手工走查表 + GIF）

**FR-U1 登录注册页**：表单校验错误可见；登录成功进入文档页；token 过期自动跳转登录。
**FR-U2 文档页**：上传（含进行中状态）、列表轮询刷新状态、删除带确认。
**FR-U3 聊天页**：流式逐字渲染；sources 折叠展开；会话切换与新建；错误以 toast 呈现。
- [ ] 附一份 10 条手工走查清单（登录→上传→等 READY→提问→展开来源→删除→登出），全部打勾
- [ ] 完整流程录制 30 秒 GIF 并入 README

## 3. 非功能需求（NFR）

**NFR-1 安全**
- BCrypt 强度 ≥ 10；JWT 密钥 ≥ 256 bit，仅经环境变量注入
- 上传文件名清洗，存储路径与用户输入解耦（防路径穿越）；上传目录不可执行
- CORS 白名单仅允许前端域
- 免费档合规：演示语料仅使用公开文档（年报、招股书等），任何私密文件不经免费档 API

**NFR-2 性能（目标值，Gate 3 实测后修订；受免费档限流影响属已知约束）**
- 问答首 token：P50 < 3 s，P95 < 8 s
- 上传接口响应 < 2 s（解析异步化保证）
- 20 MB 文本型 PDF 完成向量化 < 5 分钟（含退避重试时间）

**NFR-3 可靠性**
- 单机目标；容器重启后数据完好（卷持久化验证）
- 单文档 ingestion 失败不影响已 READY 文档的检索与问答

**NFR-4 可维护性**
- 模块间不横向 import（auth / document / ingestion / chat 边界，违例以 ArchUnit 测试拦截，P1 起生效）
- 核心逻辑有单元测试；三个深度红线部位（见 00-BRIEF）本人可脱稿重写

**NFR-5 可观测**
- 结构化日志，含 requestId 贯穿一次请求；Actuator health（P1）

**NFR-6 成本**
- 月度 LLM API 成本 = 0；云支出 < USD 15/月（仅 Gate 3 起）

## 4. P1 / P2 条目（验收进入 Gate 3 前补齐）

- P1：AWS 单机部署；S3 文件存储；GitHub Actions 流水线；Actuator；每用户提问限流；会话重命名；失败文档重试
- P2：评测集（≥20 条 golden Q&A 及结果表）；hybrid search（RRF）；magic bytes 校验与 prompt injection 缓解；Postgres RLS 对比实现；轻量 rerank

## 5. FR ↔ Phase 对照（供 04-TASKPLAN 引用）

| Phase | 覆盖 FR |
|---|---|
| 0 骨架 | FR-E5，FR-E1（雏形） |
| 1 文档域 | FR-D1/D2/D4、FR-D5（部分：行与文件删除）、FR-E2/E4 |
| 2 认证隔离 | FR-A1–A4、FR-A5（文档侧）、FR-D 系补齐 user 维度 |
| 3 RAG v1 | FR-D3/D6、FR-D5（完整：向量级联）、FR-Q1/Q2/Q4、FR-E3、FR-A5（检索层） |
| 4 流式会话 | FR-Q3/Q5/Q6、FR-A5（会话侧） |
| 5 前端 | FR-U1–U3 |
| 6 收尾 | FR-E1（正式验收）、README、P1 启动 |
