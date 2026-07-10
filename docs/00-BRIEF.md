# 00 · 项目简报（Project Brief）

> **用途**：三个顾客共用的入口——三个月后的你、每一次新开的 AI 会话（本文件 + 当前任务卡 + 相关 ADR = context kit）、以及面试官。保持一页以内。
> **状态**：v1.0 定稿（2026-07-10）｜一致性校验通过

## 一句话定位

**Portfolio RAG** —— 面向个人与小团队的私有知识库问答平台：上传 PDF / TXT / Markdown 文档，经 RAG 流水线切块、向量化存入 pgvector；用户以自然语言提问，系统检索最相关片段并结合 LLM **流式**生成**带来源引用**的回答。演示场景定位为**金融文档问答**（年报、招股书、合规手册），服务香港 fintech 求职叙事——架构通用，叙事垂直。

## 项目要达成什么（按优先级）

1. **作品集**：2026-10-04 前达到 Gate 2，成为投递 KL 实习与后续 HK 求职的旗舰项目。
2. **技能**：借项目补齐并做深 Spring Boot、Spring Security/JWT、React/Next.js、REST 设计、AWS、Docker 的实战能力。
3. **可信深度**：项目每一处关键实现都经过本人理解与验收，能承受面试三层追问。

## 技术栈（已定，理由见 docs/adr/）

| 层 | 选型 | ADR |
|---|---|---|
| 后端 | Java 21 · Spring Boot 3.x · Spring AI · Maven | 001 |
| 架构形态 | 模块化单体：auth / document / ingestion / chat 四模块 | 002 |
| 数据 | PostgreSQL 16 + pgvector（关系数据与向量同库）· Flyway 迁移 | 003 |
| 认证与隔离 | Spring Security 6 + JWT（短效 access + refresh）；全资源按 user_id 隔离，跨用户返回 404 | 004 |
| 流式 | SSE（事件协议：token / sources / done / error） | 005 |
| 前端 | Next.js（App Router，先按 SPA 风格写）· Tailwind | 006 |
| 交付 | Docker Compose；GitHub Actions；AWS EC2/Lightsail（Gate 3 起） | 007 |
| 模型 | 对话：Gemini Flash 与 Groq 双 provider，均走 OpenAI 兼容端点，Spring profile 切换；embedding：Gemini Embedding，输出截断 768 维（锁定效应，郑重已选，迁移预案见 ADR-008）；均支持中英文；本地 Ollama 兜生成端故障，真断网演示走 README GIF | 008 |

## 硬约束

- **预算**：LLM API 走免费档；限流用工程手段消化（批处理、429 指数退避）。免费档数据可能被服务商用于训练 → **只投喂公开文档**（详见 NFR）。云支出 < USD 15/月，且仅 Gate 3 起产生。
- **时间**：弹性排期，不锁日历日期。总预算约 12 周 × 每周 10 小时；唯一外部锚点：**Gate 2 须在首次投递实习之前完成**（锚点来自招聘窗口，非本计划设定）。每两周在周记核对所处 Phase，累计落后超过两周即启动砍功能顺序：Stretch → 云部署 → 前端美化。
- **人力**：单人开发 + AI 辅助。AI 按任务卡执行（见 04-TASKPLAN），一张卡 = 一次会话 = 一个 commit。
- **深度红线**：Security 过滤器链、检索拼 prompt、SSE controller 三处必须能脱稿在白板上重写伪代码；任务卡理解题不过关，该卡不算完成。
- **语言分工**：内部工作文档（docs/ 全部）用中文求快；README、代码、注释、commit message 用英文面向面试官；ADR 先中文成稿，Gate 2 收尾周统一补英文摘要。

## 里程碑

| 门 | 覆盖 | 通过判据 |
|---|---|---|
| Gate 1 · 技术 MVP | Phase 0–4（约 8 周量） | curl/Postman 走通 注册→上传→流式问答带来源；隔离测试全绿；main 可跑 |
| Gate 2 · 作品集 MVP | + 简版前端 + 工程收尾（约 4 周量） | `docker compose up` 一键起；README 含架构图与 30 秒 GIF；**先于首次投递** |
| Gate 3 · 完整版 | 云部署 + Stretch（投递期并行） | 线上地址可访问；评测结果表进 README |

## 名词表（10 条速查）

- **RAG**：检索增强生成——先从知识库检索相关片段，再交给 LLM 依据片段作答，抑制幻觉。
- **Embedding**：把文本映射为高维向量，语义相近的文本向量距离近。**换 embedding 模型 = 全量重建索引**（锁定效应）。
- **Chunk**：文档切成的片段，向量化与检索的基本单位。
- **余弦相似度**：衡量两向量方向接近程度的指标，RAG 检索的默认距离度量。
- **top-k**：检索时取相似度最高的 k 个 chunk。
- **pgvector / HNSW**：Postgres 的向量扩展及其近似最近邻索引；HNSW 索引维度上限约 2000。
- **SSE**：Server-Sent Events，HTTP 上的单向服务端推送，本项目流式输出的载体。
- **JWT**：签名的令牌，三段结构 header.payload.signature；签名保真伪，不保密文。
- **ADR**：架构决策记录——背景 / 选项 / 决定 / 后果，一个决策一页。
- **Testcontainers**：测试时用 Docker 拉起真实依赖（如 Postgres）的库，让集成测试可信。

## 工作方式（三条纪律）

1. SCOPE 之外的一切想法先进 `BACKLOG.md`，绝不直接动手。
2. main 分支永远可跑，功能在分支上做；一卡一 commit，AI 写砸即 revert。
3. 每个 Phase 开工先上概念课：AI 讲 → 本人复述 → 两道白板题过关 → 才写代码。
