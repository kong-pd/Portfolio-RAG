# CLAUDE.md · Portfolio RAG 模型工作宪章

> 你（AI 模型）在本仓库工作的最高规则。与人类的即时指令冲突时，先指出冲突，再执行人类的最终决定。

## 项目一句话

面向个人与小团队的私有知识库问答平台：RAG + 来源引用 + SSE 流式，多用户 JWT 隔离，演示叙事定位金融文档问答。详见 `docs/00-BRIEF.md`。

## 开工前必读（按序，读完才许写码）

1. `docs/00-BRIEF.md`（目标、约束、深度红线）
2. `docs/04-TASKPLAN.md` 中当前任务卡
3. 卡内引用的 `docs/adr/` 与 `docs/03-ARCHITECTURE.md` 章节

## 技术栈（锁定，任何改动走 ADR 变更流程）

Java 21 · Spring Boot 3.x · Spring AI · Maven · PostgreSQL 16 + pgvector（`vector(768)`，HNSW cosine）· Flyway · Next.js App Router（SPA 风格）· Tailwind · Docker Compose。
模型层：对话 = Gemini Flash / Groq，OpenAI 兼容端点，Spring profile 切换；embedding = **Gemini Embedding 截断 768 维，严禁擅自更换**（锁定效应，换模型 = 全量重建索引，见 ADR-008）。

## 模块与依赖（ADR-002）

`auth / document / ingestion / chat / common`。允许的依赖方向：document → ingestion（触发）；各模块 → common。**其余横向 import 一律禁止**；发现需要新的跨模块调用 → 停下报告，不擅自连线。

## 工作流（与 docs/04 §0 完全一致）

1. 一张卡 = 一次会话 = 一个 commit，message 带卡号：`feat(T2.3): jwt auth filter`；纯文档用 `docs:`。
2. 先交实现计划（涉及文件清单 + 每文件要点），等用户确认，再写代码。
3. 写完逐条对照卡内验收清单自检，汇报每条的验证方式与结果。
4. 讲解关键代码，用卡内理解题考用户；答不出就讲到能答出，否则不关卡。
5. 执行颗粒度可申请按整 Phase 伸缩（先交整 Phase 计划）；**验收颗粒度永不伸缩**。

## 行为边界（硬约束）

- 禁止大规模重构；重构冲动 → 一句话记入 `docs/BACKLOG.md`。
- 禁止修改 `docs/02` 的验收标准与 `docs/01` 的范围；范围外想法 → BACKLOG。
- 禁止未经卡内说明或用户批准引入新依赖。
- 密钥永不入库；配置一律环境变量，`.env.example` 同步维护。
- main 分支永远可跑；功能在分支上做。
- **三个深度红线部位**（Security 过滤器链、检索→拼 prompt、SSE controller）：你的角色是讲解、伪代码引导与 review，**首发实现由用户手写**。

## 异议义务

若你依据当前证据判断某 ADR 已属次优，你有义务主动提出，引用该 ADR 的"重审触发"条款，按 `docs/01` §0 的变更三规则提案。沉默的顺从视为失职。

## 接手审计（任何新模型上岗的第一件事）

1. 读本文件与 `docs/00-BRIEF.md`；
2. `git log --oneline -10`，对照 `docs/04` 的卡片勾选状态；
3. 跑 `mvn test`（跑不了要说明原因）；
4. 报告"文档 vs 代码现状"的一切偏差；
5. 审计报告获用户确认后，才领卡开工。

## 常用命令

- 起依赖：`docker compose up -d`
- 测试：`mvn test`（隔离测试走 Testcontainers，需本机 Docker 运行中）
- 迁移文件位置：`src/main/resources/db/migration`
