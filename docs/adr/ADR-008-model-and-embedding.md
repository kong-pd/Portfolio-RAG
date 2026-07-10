# ADR-008 · 模型与 embedding 选型（本包内唯一"郑重选"）

**状态**：Accepted（2026-07-10）

**背景**：预算约束 API 走免费档；FR-D6 要求中英文；**embedding 有锁定效应**——换模型 = 全量重建索引，且查询时 embedding 必须与入库同模型，dev 与 prod 必须一致。

**对话模型（可随意切，低风险）**：双 provider——Gemini Flash 系与 Groq，均走 OpenAI 兼容端点，同一套 Spring AI OpenAI client 配不同 base-url，以 Spring profile 切换；另设 `demo-offline` profile 走本地 Ollama 作断网演示 fallback。429 处理按 FR-E3。

**Embedding（郑重选）**：
- 候选 A：Gemini Embedding，免费档可用、多语言强、支持输出维度截断。
- 候选 B：Ollama + bge-m3，本地免费、中文强、无限流；但**部署机也必须跑得动它**——目标 EC2 小机型内存撑不住，dev/prod 一致性被破坏。

**决定**：候选 A，Gemini Embedding，**输出维度截断至 768**。理由按权重排序：(1) dev 与 prod 天然同源，部署机零模型负担；(2) 768 维在 pgvector HNSW 约 2000 维上限内，检索质量足够；(3) 多语言覆盖 FR-D6；(4) 免费档 embedding 侧额度相对慷慨（以 AI Studio 实时面板为准，不信旧博客数字）。

**后果**：表结构定为 `vector(768)` + HNSW cosine；依赖 Google 免费档政策存续——迁移预案：切 bge-m3 并全量重建索引，预估一个工作日；免费档数据可能用于训练 → NFR-1 已限定只投喂公开文档。
**重审触发**：免费档政策收紧，或评测集（P2）显示中文检索质量不达标。
